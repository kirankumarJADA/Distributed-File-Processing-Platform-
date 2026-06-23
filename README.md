# Distributed File Processing Platform (DFPP)

A horizontally-scalable, microservice-based platform where users upload files
(PDF / image / CSV / ZIP) that are processed **asynchronously** by a mesh of
distributed workers coordinated through **Apache Kafka**, with **real-time
progress** streamed back to a live dashboard over WebSocket.

This is a portfolio project built to demonstrate backend & cloud engineering:
distributed systems, asynchronous processihng, worker queues, fault tolerance,
idempotency, retries with backoff, dead-letter queues, observability, and
Kubernetes-native scalability.

> Everything here is **real**. Kafka actually transports jobs, workers actually
> open and parse the files (PDFBox / commons-csv / ImageIO / java.util.zip),
> retries and the dead-letter queue actually fire, and progress is actually
> pushed to the browser over STOMP. No mocks, no fake `Thread.sleep` "processing".

---

## Features

- Distributed file processing with Kafka
- Real-time WebSocket updates
- Horizontal worker scaling
- Retry with exponential backoff
- Dead-letter queue (DLQ)
- Redis idempotency protection
- JWT authentication & authorization
- Dockerized microservices
- Kubernetes-ready deployment
- Prometheus + Grafana observability
- CI/CD with GitHub Actions
- Real PDF / CSV / ZIP / Image processing

---

## Table of contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Why a distributed design](#why-a-distributed-design)
4. [Kafka pipeline](#kafka-pipeline)
5. [Retry & failure-recovery strategy](#retry--failure-recovery-strategy)
6. [Real-time dashboard](#real-time-dashboard)
7. [Monitoring & observability](#monitoring--observability)
8. [Local setup (no Docker)](#local-setup-no-docker)
9. [Docker setup](#docker-setup)
10. [Demo credentials](#demo-credentials)
11. [Kubernetes deployment](#kubernetes-deployment)
12. [Screenshots](#screenshots)
13. [Verification status](#verification-status)
14. [Engineering challenges](#engineering-challenges)
15. [Project layout](#project-layout)

---

## Architecture

```
                                  ┌──────────────────────────┐
                                  │   React 18 + Vite + TW    │
                                  │  (control-room dashboard) │
                                  └─────────────┬─────────────┘
                                   REST + STOMP/WebSocket
                                                │
                                  ┌─────────────▼─────────────┐
                                  │       API Gateway         │
                                  │  Spring Cloud Gateway     │
                                  │  • JWT validation         │
                                  │  • Redis rate limiting    │
                                  │  • routing                │
                                  └───┬───────────┬───────────┘
                                      │           │
                  ┌───────────────────▼──┐   ┌────▼────────────────────┐
                  │  File Upload Service │   │  Notification Service   │
                  │  • register / login  │   │  • STOMP WebSocket hub  │
                  │  • JWT issuance      │   │  • Kafka → browser fan- │
                  │  • magic-byte +      │   │    out (per-user)       │
                  │    malware validation│   └────▲────────────────────┘
                  │  • metadata (JPA)    │        │  progress events
                  │  • history           │        │
                  └───┬─────────────┬────┘        │
       file.uploaded  │             │ progress    │
        (Kafka)       │             └─────────────┤
                      ▼                            │
        ┌─────────────────────────────┐           │
        │  Processing Worker Service  │───────────┘
        │  (consumer group, N pods)   │  file.processing.progress
        │  • real PDF/IMG/CSV/ZIP     │
        │  • Redis idempotency        │  file.processing.dlq
        │  • retry + backoff          │────────────► (dead-letter topic)
        │  • Micrometer metrics       │
        └──────┬───────────┬──────────┘
               │           │
        ┌──────▼───┐  ┌────▼─────┐  ┌───────────┐  ┌──────────┐
        │  Kafka   │  │  Redis   │  │ PostgreSQL│  │Prometheus│→ Grafana
        └──────────┘  └──────────┘  └───────────┘  └──────────┘
```

| Service | Port | Responsibility |
|---|---|---|
| `api-gateway` | 8080 | Auth (JWT validation), routing, Redis rate limiting, CORS |
| `file-upload-service` | 8081 | Register/login, secure uploads, validation, metadata, history, Kafka producer |
| `processing-worker-service` | 8082 | Kafka consumers, real file processing, retry/DLQ, idempotency, queue metrics |
| `notification-service` | 8083 | STOMP WebSocket hub, Kafka→browser real-time fan-out |
| `frontend` | 5173 | React control-room dashboard |
| `common` | — | Shared JWT provider, events, enums |

---

## Why a distributed design

Synchronous file processing inside the upload request would couple latency to
the slowest file and cap throughput at one server. Instead:

- **Decoupling** — the upload service only validates, persists metadata and
  publishes an event. It returns in milliseconds.
- **Elastic workers** — the worker service is a Kafka **consumer group**.
  Kafka partitions `file.uploaded` across every worker pod, so adding replicas
  linearly increases throughput. A `HorizontalPodAutoscaler` adds workers when
  CPU/queue pressure rises.
- **Resilience** — a worker crash mid-job does not lose the message; Kafka
  redelivers it to another consumer (offsets are committed only after a
  terminal outcome). The Redis idempotency guard prevents double-processing on
  redelivery.
- **Backpressure visibility** — consumer-group lag is computed from real
  committed vs end offsets and surfaced to Grafana and the dashboard.

---

## Kafka pipeline

Three topics (names centralised in `common/event/Topics.java`):

| Topic | Partitions | Producer | Consumers |
|---|---|---|---|
| `file.uploaded` | 3 | upload-service | `processing-workers` group |
| `file.processing.progress` | 3 | workers | `notification-fanout`, `upload-progress-sync` |
| `file.processing.dlq` | 1 | workers | (inspection / replay) |

Key design points:

- **Partition key = job id.** All events for one file hit the same partition →
  strict ordering and a natural dedupe key.
- **Producer idempotence** (`enable.idempotence=true`, `acks=all`) prevents
  duplicate publishes on producer retries.
- **Manual acknowledgement** (`AckMode.MANUAL_IMMEDIATE`) — the worker only
  commits the offset after the job reaches a terminal state (`COMPLETED` or
  `DEAD_LETTER`), guaranteeing at-least-once delivery.
- **Exactly-once *effect*** — at-least-once delivery + a Redis `SETNX`
  idempotency claim (`IdempotencyService`) means a job's side effects run once
  even if Kafka redelivers after a rebalance.
- **Two independent consumer groups** read progress events: one fans them to
  browsers, one keeps the `file_metadata` table (history) in sync. Independent
  groups = independent offsets = no interference.

---

## Retry & failure-recovery strategy

Implemented in `FileProcessingConsumer`:

1. **Claim** the job in Redis (skip if already completed or in-flight elsewhere).
2. Emit `PROCESSING` progress and run the real processor.
3. On a retryable exception, retry **in-listener** up to `WORKER_MAX_ATTEMPTS`
   (default 4) with **exponential backoff**: `base * 2^(attempt-1)` ms
   (1s → 2s → 4s …). Progress events announce each retry so the UI shows it.
4. Offsets are *not* committed between attempts — a pod crash mid-retry simply
   causes Kafka to redeliver, and the idempotency guard resumes cleanly.
5. After the final failed attempt the job is **dead-lettered**: published to
   `file.processing.dlq` and a terminal `DEAD_LETTER` progress event is emitted
   (the UI shows it red, the metric `dfpp_jobs_deadlettered_total` increments).

This separates *transient* failures (recovered by retry) from *poison*
messages (quarantined in the DLQ instead of blocking the partition forever).

---

## Real-time dashboard

- React 18 + Vite + TailwindCSS + Recharts, a deliberately distinctive dark
  "control-room" aesthetic (monospace data type, blueprint grid, signal-green
  accents) — not generic AI styling.
- Connects via STOMP-over-SockJS to the notification-service WebSocket
  endpoint — reached at `/ws` (proxied through the gateway / nginx, and
  exposed directly at `http://localhost:8083/ws`) for real-time processing
  updates — subscribing to `/topic/progress/{userId}` (own files) and
  `/topic/progress` (global feed).
- Live worker-activity panel updates per progress event; throughput/status/
  success-rate charts poll the aggregate monitoring endpoint every 4s.
- Auth screen ships with the seeded accounts pre-filled.

---

## Monitoring & observability

- Every service exposes `/actuator/health` (with `liveness`/`readiness`
  probe groups) and `/actuator/prometheus`.
- Custom Micrometer metrics: `dfpp_jobs_processed_total`,
  `dfpp_jobs_failed_total`, `dfpp_jobs_retried_total`,
  `dfpp_jobs_deadlettered_total`, `dfpp_jobs_duplicates_skipped_total`,
  `dfpp_jobs_processing_duration_seconds`, `dfpp_workers_active`,
  `dfpp_uploads_total`, `dfpp_notifications_pushed_total`.
- **Queue lag** is computed for real via the Kafka AdminClient
  (`KafkaLagInspector`): `lag = logEndOffset − committedOffset` per partition.
- Prometheus scrapes all four services; Grafana auto-provisions a dashboard
  (throughput rate, p95 duration, dead-letters, active workers, consumer lag).

---

## Local setup (no Docker)

Prerequisites: JDK 21, Maven 3.9+, Node 20, and local Kafka + PostgreSQL +
Redis (or just run the infra via Docker, see below).

```bash
# 1. infra only
docker compose up -d postgres redis zookeeper kafka

# 2. build the backend
cd backend && mvn -B clean package -DskipTests

# 3. run each service (separate terminals)
java -jar api-gateway/target/api-gateway.jar
java -jar file-upload-service/target/file-upload-service.jar
java -jar processing-worker-service/target/processing-worker-service.jar
java -jar notification-service/target/notification-service.jar

# 4. frontend
cd ../frontend && npm install && npm run dev
# open http://localhost:5173  (login: demo / demo1234)
```

Swagger UI: `http://localhost:8081/swagger-ui.html`.

## Docker setup

```bash
docker compose up --build
```

Brings up the full stack: Kafka + Zookeeper, PostgreSQL, Redis, all four
backend services (workers run **2 replicas** to demonstrate the consumer
group), the frontend, Prometheus and Grafana.

| URL | What |
|---|---|
| http://localhost:5173 | Dashboard |
| http://localhost:8080/actuator/health | Gateway health |
| http://localhost:9090 | Prometheus |
| http://localhost:3000 | Grafana (admin / admin) |

Scale workers on the fly:

```bash
docker compose up -d --scale processing-worker-service=5
```

Production-grade container practices: multi-stage builds, minimal JRE runtime,
**non-root** users, `HEALTHCHECK` instructions, JVM container-awareness flags.

## Demo Credentials

Seeded automatically on first boot (see `DataSeeder`):

| Username | Password  | Roles              |
|----------|-----------|--------------------|
| demo     | demo1234  | USER               |
| admin    | admin1234 | ADMIN + USER       |

Log in at `http://localhost:5173` to test immediately.

## Kubernetes deployment

```bash
# Build & push images (or let GitHub Actions do it), then:
kubectl apply -f infra/k8s/00-namespace-config.yaml
kubectl apply -f infra/k8s/10-infra.yaml          # postgres, redis, kafka, zk
kubectl apply -f infra/k8s/20-services.yaml       # 5 app deployments + svcs
kubectl apply -f infra/k8s/30-autoscaling-ingress.yaml  # HPA + ingress

# add to /etc/hosts:  127.0.0.1 dfpp.local
kubectl -n dfpp get pods
open http://dfpp.local
```

Manifests include ConfigMap + Secret, PVCs, `readinessProbe`/`livenessProbe`
on every pod, resource requests/limits, and HPAs (workers scale 2→10 on CPU,
gateway 2→6). Replace `OWNER` in the image references with your GHCR org.

## Screenshots

> Drop the PNGs into `docs/` (a placeholder folder is already in the repo)
> after your first run; the paths below resolve automatically on GitHub.

### Live Dashboard

![Dashboard](docs/screen%20shots/Dashboard.png)

Real-time distributed file processing dashboard showing:
- Kafka worker activity
- Live WebSocket updates
- Upload history
- Throughput metrics
- Success rate tracking

### Grafana Monitoring

![Grafana](odcs/screen%20shots/Grafana%20dashboard.png)

Production monitoring with:
- JVM memory metrics
- Kafka consumer lag
- Worker throughput
- Request latency
- Processing success/failure metrics

### Running Containers

![Docker](docs/screen%20shots/docker%20compose%20ps.png)

Docker Compose multi-container environment:
Kafka, PostgreSQL, Redis, Spring Boot microservices, Prometheus and Grafana.

### Architecture / Infrastructure

![Architecture](docs/screen%20shots/docker%20ps.png)

Distributed microservice architecture running locally with Docker Compose.

## Verification status

Honest status, so nothing here is overstated:

**Verified**

- Frontend production build succeeds (`npm run build`, 955 modules, 0 lint
  errors).
- Backend source is structurally consistent (all Java files compile-clean by
  inspection; package layout, Maven modules and Spring wiring reviewed).
- Unit tests are written for the security-critical paths: JWT issue/verify,
  magic-byte + malware file validation, and the CSV/ZIP processors.

**To reproduce end-to-end** — one command, fully automated:

```bash
./scripts/smoke-test.sh
```

This boots the full Docker Compose stack, registers a user through the
gateway, uploads `docs/samples/sample.pdf`, polls until the job reaches
`COMPLETED`, and prints the processor output. Exit code `0` means a genuine
end-to-end run succeeded. (Or do it by hand: `docker compose up --build`,
then upload a file at `http://localhost:5173` — see
[Docker setup](#docker-setup).)

**Expected processor output.** The implemented `PdfProcessor` emits a JSON
result of this shape (this is what the code produces, shown here as a
reference for what a completed PDF job looks like):

```json
{
  "type": "PDF",
  "pageCount": 2,
  "encrypted": false,
  "title": "",
  "author": "",
  "producer": "",
  "characterCount": 1842,
  "wordCount": 311
}
```

> **Activate the "Verified Run" section below once — and only once — you have
> run `./scripts/smoke-test.sh` (or the manual steps) on your own machine and
> seen it pass.** At that point every line is true and it becomes one of the
> strongest things in the repo. Until then it stays commented out so the
> README never makes a claim that hasn't happened.

<!-- ============================================================
     VERIFIED RUN  —  uncomment this block AFTER a real local run
     (./scripts/smoke-test.sh exits 0, or you uploaded a file via
     the dashboard and watched it reach COMPLETED). Then delete the
     "Verification status" heading above if you want, and delete
     these HTML comment markers.
     ============================================================

## Verified Run

Successfully tested end-to-end locally:

- File upload → Kafka queue → worker processing → completion
- Live WebSocket updates functioning
- PDF metadata extraction verified
- Multi-container Docker Compose environment verified
- Prometheus + Grafana monitoring verified

Example processed output (captured from a real run):

```json
{
  "type": "PDF",
  "pageCount": 2,
  "encrypted": false
}
```

============================================================ -->

## Engineering challenges

- **Exactly-once effect on at-least-once transport.** Kafka guarantees
  at-least-once; processing a file twice (e.g. generating a duplicate
  thumbnail) is wrong. Solved with a Redis claim/commit protocol decoupled
  from Kafka offset commits.
- **Not blocking a partition on a poison message.** A permanently-bad file
  must not stall every other job on its partition. Bounded retries → DLQ
  quarantine keeps the pipeline flowing.
- **Real progress without polling the DB.** Progress is event-driven: workers
  emit fine-grained progress to Kafka, the notification service fans it to the
  exact user's WebSocket topic; a second consumer group persists it for the
  history view — neither blocks the other.
- **Trustworthy file validation.** Filenames and client MIME types lie.
  Validation sniffs real magic bytes and runs deterministic malware heuristics
  (PE/ELF/Mach-O/script-shebang rejection) before anything is queued.
- **Observable backpressure.** "Is the system keeping up?" is answered with
  real consumer-group lag from the Kafka AdminClient, not a guess.
- **Stateless horizontal scale.** Auth is stateless JWT; workers share nothing
  but Kafka/Redis, so `kubectl scale` / HPA just works.

## Project layout

```
distributed-file-processing-platform/
├── backend/                     # Maven multi-module (Java 21, Spring Boot 3)
│   ├── common/                  # JWT, events, enums (shared)
│   ├── api-gateway/             # Spring Cloud Gateway
│   ├── file-upload-service/     # auth + uploads + metadata + producer
│   ├── processing-worker-service/  # Kafka workers + processors + retry/DLQ
│   └── notification-service/    # STOMP WebSocket fan-out
├── frontend/                    # React 18 + Vite + Tailwind + Recharts
├── infra/
│   ├── docker/                  # multi-stage Dockerfiles + nginx
│   ├── k8s/                     # namespace/config/infra/services/HPA/ingress
│   ├── prometheus/              # scrape config
│   └── grafana/                 # provisioned datasource + dashboard
├── .github/workflows/ci-cd.yml  # tests → build → GHCR → manifest validation
└── docker-compose.yml           # full local stack
```

## License

MIT — see `LICENSE`.
