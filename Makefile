.PHONY: infra up down build test fe k8s

infra:        ## start kafka/postgres/redis only
	docker compose up -d postgres redis zookeeper kafka

up:           ## full stack
	docker compose up --build

down:
	docker compose down -v

build:        ## build backend jars
	cd backend && mvn -B clean package -DskipTests

test:         ## run all backend tests
	cd backend && mvn -B test

fe:           ## run frontend dev server
	cd frontend && npm install && npm run dev

k8s:          ## apply all k8s manifests
	kubectl apply -f infra/k8s/00-namespace-config.yaml
	kubectl apply -f infra/k8s/10-infra.yaml
	kubectl apply -f infra/k8s/20-services.yaml
	kubectl apply -f infra/k8s/30-autoscaling-ingress.yaml
