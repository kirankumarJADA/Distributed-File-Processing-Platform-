import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { api, auth } from '../lib/api';
import { useLiveProgress } from '../lib/useLiveProgress';
import { Panel, ProgressBar, Stat, StatusPill } from './ui';
import Uploader from './Uploader';

export default function Dashboard({ user, onLogout }) {
  const { connected, events } = useLiveProgress(user.id);
  const [history, setHistory] = useState([]);
  const [mon, setMon] = useState(null);
  const [throughput, setThroughput] = useState([]);

  const loadHistory = useCallback(async () => {
    try {
      setHistory(await api.history());
    } catch {
      /* ignore transient */
    }
  }, []);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  // Re-fetch history whenever a terminal event arrives
  useEffect(() => {
    if (events[0] && ['COMPLETED', 'DEAD_LETTER', 'FAILED'].includes(events[0].status)) {
      loadHistory();
    }
  }, [events, loadHistory]);

  // Poll aggregate monitoring + build a rolling throughput series
  useEffect(() => {
    let alive = true;
    const tick = async () => {
      try {
        const m = await api.monitoring();
        if (!alive) return;
        setMon(m);
        setThroughput((prev) =>
          [...prev, { t: new Date().toLocaleTimeString().slice(0, 8), processed: m.processed, failed: m.failed }].slice(-30),
        );
      } catch {
        /* worker may still be warming up */
      }
    };
    tick();
    const id = setInterval(tick, 4000);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, []);

  // Live in-flight jobs derived from the WS stream
  const live = useMemo(() => {
    const byJob = new Map();
    for (const e of events) if (!byJob.has(e.jobId)) byJob.set(e.jobId, e);
    return [...byJob.values()].slice(0, 8);
  }, [events]);

  const statusData = useMemo(() => {
    const c = { COMPLETED: 0, PROCESSING: 0, QUEUED: 0, FAILED: 0, DEAD_LETTER: 0 };
    history.forEach((h) => {
      c[h.status] = (c[h.status] || 0) + 1;
    });
    return Object.entries(c).map(([name, value]) => ({ name, value }));
  }, [history]);

  const barColor = { COMPLETED: '#3ef0c4', PROCESSING: '#5b8def', QUEUED: '#ffb454', FAILED: '#ff5d73', DEAD_LETTER: '#ff5d73' };

  return (
    <div className="relative z-10 mx-auto max-w-7xl px-6 py-6">
      {/* Header */}
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="font-mono text-[10px] uppercase tracking-[0.4em] text-signal">
            distributed file processing platform
          </div>
          <h1 className="font-display text-2xl font-bold text-wire">
            DFPP<span className="text-signal">_</span>CONTROL
          </h1>
        </div>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 rounded-full border border-edge bg-panel px-3 py-1.5">
            <i
              className={`h-2 w-2 rounded-full ${connected ? 'bg-signal animate-pulseDot' : 'bg-plasma'}`}
            />
            <span className="font-mono text-[10px] uppercase tracking-widest text-ghost">
              ws {connected ? 'live' : 'offline'}
            </span>
          </div>
          <div className="text-right">
            <div className="text-sm font-semibold text-wire">{user.username}</div>
            <div className="font-mono text-[10px] text-mute">{(user.roles || []).join(' · ')}</div>
          </div>
          <button
            onClick={onLogout}
            className="rounded-lg border border-edge px-3 py-2 font-mono text-[10px] uppercase tracking-widest text-mute transition hover:border-plasma/50 hover:text-plasma"
          >
            sign out
          </button>
        </div>
      </header>

      {/* KPI row */}
      <div className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-5">
        <Stat label="Processed" value={fmt(mon?.processed)} accent="signal" />
        <Stat label="Failed" value={fmt(mon?.failed)} accent="plasma" />
        <Stat label="Retried" value={fmt(mon?.retried)} accent="amber" />
        <Stat label="Dead-letter" value={fmt(mon?.deadLettered)} accent="plasma" />
        <Stat
          label="Queue lag"
          value={mon?.queue?.totalLag >= 0 ? fmt(mon?.queue?.totalLag) : '—'}
          accent="cobalt"
          sub={`${mon?.activeWorkers ?? 0} active workers`}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Left column */}
        <div className="space-y-6 lg:col-span-2">
          <Panel title="Throughput" tag="processed vs failed · 4s poll" accent="cobalt">
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={throughput}>
                <defs>
                  <linearGradient id="g1" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#3ef0c4" stopOpacity={0.5} />
                    <stop offset="100%" stopColor="#3ef0c4" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="g2" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#ff5d73" stopOpacity={0.4} />
                    <stop offset="100%" stopColor="#ff5d73" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#1d2735" strokeDasharray="3 3" />
                <XAxis dataKey="t" stroke="#5b6b80" fontSize={10} />
                <YAxis stroke="#5b6b80" fontSize={10} allowDecimals={false} />
                <Tooltip
                  contentStyle={{ background: '#0d1219', border: '1px solid #1d2735', borderRadius: 8, fontSize: 12 }}
                />
                <Area type="monotone" dataKey="processed" stroke="#3ef0c4" fill="url(#g1)" strokeWidth={2} />
                <Area type="monotone" dataKey="failed" stroke="#ff5d73" fill="url(#g2)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </Panel>

          <Panel title="Live worker activity" tag="websocket stream" accent="signal">
            {live.length === 0 ? (
              <Empty text="No active jobs — upload a file to see the pipeline move." />
            ) : (
              <ul className="space-y-3">
                {live.map((e) => (
                  <li key={e.jobId} className="rounded-lg border border-edge bg-panel2/60 px-4 py-3 animate-rise">
                    <div className="flex items-center justify-between">
                      <span className="truncate font-mono text-xs text-ghost">
                        job {e.jobId.slice(0, 8)} · {e.worker}
                      </span>
                      <StatusPill status={e.status} />
                    </div>
                    <div className="mt-2.5">
                      <ProgressBar value={e.progress} status={e.status} />
                    </div>
                    <div className="mt-2 flex justify-between font-mono text-[10px] text-mute">
                      <span className="truncate">{e.message}</span>
                      <span>attempt {e.attempt}</span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Panel>

          <Panel title="Upload history" tag={`${history.length} files`}>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="font-mono text-[10px] uppercase tracking-widest text-mute">
                    <th className="pb-3 pr-4">File</th>
                    <th className="pb-3 pr-4">Type</th>
                    <th className="pb-3 pr-4">Status</th>
                    <th className="pb-3 pr-4 w-40">Progress</th>
                    <th className="pb-3">Result</th>
                  </tr>
                </thead>
                <tbody className="text-ghost">
                  {history.length === 0 && (
                    <tr>
                      <td colSpan={5} className="py-6 text-center text-mute">
                        Nothing uploaded yet.
                      </td>
                    </tr>
                  )}
                  {history.map((h) => (
                    <tr key={h.id} className="border-t border-edge/60">
                      <td className="py-3 pr-4 max-w-[180px] truncate text-wire">{h.originalName}</td>
                      <td className="py-3 pr-4 font-mono text-xs">{h.fileType}</td>
                      <td className="py-3 pr-4">
                        <StatusPill status={h.status} />
                      </td>
                      <td className="py-3 pr-4">
                        <ProgressBar value={h.progress} status={h.status} />
                      </td>
                      <td className="py-3 max-w-[220px] truncate font-mono text-[11px] text-mute">
                        {h.resultJson ? h.resultJson : h.statusMessage}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Panel>
        </div>

        {/* Right column */}
        <div className="space-y-6">
          <Panel title="Submit a job" accent="signal">
            <Uploader onUploaded={loadHistory} />
          </Panel>

          <Panel title="Status distribution" tag="your files" accent="cobalt">
            <ResponsiveContainer width="100%" height={210}>
              <BarChart data={statusData} layout="vertical" margin={{ left: 8 }}>
                <CartesianGrid stroke="#1d2735" strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" stroke="#5b6b80" fontSize={10} allowDecimals={false} />
                <YAxis dataKey="name" type="category" stroke="#5b6b80" fontSize={9} width={86} />
                <Tooltip
                  cursor={{ fill: '#111824' }}
                  contentStyle={{ background: '#0d1219', border: '1px solid #1d2735', borderRadius: 8, fontSize: 12 }}
                />
                <Bar dataKey="value" radius={[0, 4, 4, 0]}>
                  {statusData.map((d) => (
                    <Cell key={d.name} fill={barColor[d.name] || '#5b6b80'} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </Panel>

          <Panel title="Success rate" accent="signal">
            <div className="flex items-end gap-3">
              <div className="tabular text-5xl font-bold text-signal">
                {mon ? `${mon.successRate}%` : '—'}
              </div>
              <div className="pb-2 font-mono text-[11px] text-mute">
                {fmt(mon?.processed)} ok · {fmt(mon?.failed)} err
              </div>
            </div>
            <div className="mt-4 h-2 w-full overflow-hidden rounded-full bg-edge">
              <div
                className="h-full rounded-full bg-gradient-to-r from-signal to-cobalt transition-all duration-700"
                style={{ width: `${mon?.successRate ?? 0}%` }}
              />
            </div>
          </Panel>
        </div>
      </div>

      <footer className="mt-8 border-t border-edge pt-4 font-mono text-[10px] uppercase tracking-widest text-mute">
        gateway :8080 · upload :8081 · workers :8082 · notify :8083 · kafka · redis · postgres
      </footer>
    </div>
  );
}

function fmt(n) {
  if (n == null) return '—';
  return Math.round(n).toLocaleString();
}

function Empty({ text }) {
  return (
    <div className="flex flex-col items-center justify-center py-10 text-center">
      <div className="mb-3 flex gap-1">
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="h-2 w-2 rounded-full bg-edge"
            style={{ animation: 'pulseDot 1.4s ease-in-out infinite', animationDelay: `${i * 0.2}s` }}
          />
        ))}
      </div>
      <p className="text-sm text-mute">{text}</p>
    </div>
  );
}
