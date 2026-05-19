import React from 'react';

export function Panel({ title, tag, children, accent = 'signal', className = '' }) {
  const ring =
    accent === 'plasma' ? 'before:bg-plasma' : accent === 'cobalt' ? 'before:bg-cobalt' : 'before:bg-signal';
  return (
    <section
      className={`relative overflow-hidden rounded-xl border border-edge bg-panel/80 backdrop-blur-sm
      before:absolute before:left-0 before:top-0 before:h-full before:w-[3px] ${ring} ${className}`}
    >
      {title && (
        <header className="flex items-center justify-between border-b border-edge px-5 py-3">
          <h2 className="font-mono text-[11px] uppercase tracking-[0.22em] text-ghost">{title}</h2>
          {tag && <span className="font-mono text-[10px] text-mute">{tag}</span>}
        </header>
      )}
      <div className="p-5">{children}</div>
    </section>
  );
}

export function Stat({ label, value, sub, accent = 'wire' }) {
  const color =
    accent === 'signal'
      ? 'text-signal'
      : accent === 'plasma'
        ? 'text-plasma'
        : accent === 'amber'
          ? 'text-amber'
          : accent === 'cobalt'
            ? 'text-cobalt'
            : 'text-wire';
  return (
    <div className="rounded-lg border border-edge bg-panel2/70 px-4 py-4">
      <div className="font-mono text-[10px] uppercase tracking-[0.2em] text-mute">{label}</div>
      <div className={`tabular mt-2 text-3xl font-bold ${color}`}>{value}</div>
      {sub && <div className="mt-1 text-xs text-ghost">{sub}</div>}
    </div>
  );
}

export function StatusPill({ status }) {
  const map = {
    COMPLETED: 'border-signal/40 text-signal bg-signal/10',
    PROCESSING: 'border-cobalt/40 text-cobalt bg-cobalt/10',
    QUEUED: 'border-amber/40 text-amber bg-amber/10',
    PENDING: 'border-amber/40 text-amber bg-amber/10',
    FAILED: 'border-plasma/40 text-plasma bg-plasma/10',
    DEAD_LETTER: 'border-plasma/60 text-plasma bg-plasma/15',
  };
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 font-mono text-[10px] tracking-wider ${
        map[status] || 'border-edge text-ghost'
      }`}
    >
      <i className="h-1.5 w-1.5 rounded-full bg-current animate-pulseDot" />
      {status}
    </span>
  );
}

export function ProgressBar({ value, status }) {
  const tone =
    status === 'DEAD_LETTER' || status === 'FAILED'
      ? 'bg-plasma'
      : status === 'COMPLETED'
        ? 'bg-signal'
        : 'bg-cobalt';
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-edge">
      <div
        className={`h-full rounded-full transition-all duration-500 ${tone}`}
        style={{ width: `${Math.max(2, value)}%` }}
      />
    </div>
  );
}
