import React, { useState } from 'react';
import { api, auth } from '../lib/api';
import { decodeJwt } from '../lib/jwt';

export default function Login({ onAuthed }) {
  const [mode, setMode] = useState('login');
  const [username, setUsername] = useState('demo');
  const [password, setPassword] = useState('demo1234');
  const [err, setErr] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setErr(null);
    setBusy(true);
    try {
      const fn = mode === 'login' ? api.login : api.register;
      const res = await fn(username, password);
      auth.set(res.token);
      const claims = decodeJwt(res.token);
      const user = { username: res.username, roles: res.roles, id: claims?.uid };
      auth.setUser(user);
      onAuthed(user);
    } catch (e2) {
      setErr(e2.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="relative z-10 flex min-h-screen items-center justify-center px-6">
      <div className="w-full max-w-md animate-rise">
        <div className="mb-8">
          <div className="font-mono text-[11px] uppercase tracking-[0.4em] text-signal">
            distributed · file · processing
          </div>
          <h1 className="mt-3 font-display text-4xl font-bold text-wire">
            DFPP<span className="text-signal">_</span>CONTROL
          </h1>
          <p className="mt-2 text-sm text-ghost">
            Asynchronous worker mesh · Kafka pipeline · live observability
          </p>
        </div>

        <div className="rounded-xl border border-edge bg-panel/80 p-7 shadow-glow backdrop-blur">
          <div className="mb-6 flex gap-1 rounded-lg border border-edge bg-ink p-1">
            {['login', 'register'].map((m) => (
              <button
                key={m}
                onClick={() => setMode(m)}
                className={`flex-1 rounded-md py-2 font-mono text-[11px] uppercase tracking-widest transition ${
                  mode === m ? 'bg-signal text-ink' : 'text-mute hover:text-ghost'
                }`}
              >
                {m}
              </button>
            ))}
          </div>

          <form onSubmit={submit} className="space-y-4">
            <Field label="Username" value={username} onChange={setUsername} />
            <Field label="Password" type="password" value={password} onChange={setPassword} />

            {err && (
              <div className="rounded-md border border-plasma/40 bg-plasma/10 px-3 py-2 text-xs text-plasma">
                {err}
              </div>
            )}

            <button
              disabled={busy}
              className="relative w-full overflow-hidden rounded-lg bg-signal py-3 font-mono text-[12px] font-bold uppercase tracking-[0.2em] text-ink transition hover:brightness-110 disabled:opacity-60"
            >
              {busy ? 'authenticating…' : mode === 'login' ? 'enter control room' : 'create account'}
              <span className="absolute inset-y-0 -left-1/2 w-1/3 -skew-x-12 bg-white/30 animate-sweep" />
            </button>
          </form>

          <div className="mt-6 rounded-md border border-edge bg-ink/60 p-3">
            <div className="font-mono text-[10px] uppercase tracking-widest text-mute">seeded demo accounts</div>
            <div className="mt-2 grid grid-cols-2 gap-2 font-mono text-[11px] text-ghost">
              <code className="rounded bg-panel2 px-2 py-1">admin / admin123</code>
              <code className="rounded bg-panel2 px-2 py-1">demo / demo1234</code>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Field({ label, value, onChange, type = 'text' }) {
  return (
    <label className="block">
      <span className="font-mono text-[10px] uppercase tracking-[0.2em] text-mute">{label}</span>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="mt-1.5 w-full rounded-lg border border-edge bg-ink px-3 py-2.5 text-sm text-wire outline-none transition focus:border-signal focus:shadow-glow"
      />
    </label>
  );
}
