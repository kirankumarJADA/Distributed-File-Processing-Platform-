import React, { useRef, useState } from 'react';
import { api } from '../lib/api';

export default function Uploader({ onUploaded }) {
  const input = useRef(null);
  const [drag, setDrag] = useState(false);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState(null);

  async function send(file) {
    if (!file) return;
    setBusy(true);
    setMsg(null);
    try {
      const res = await api.uploadFile(file);
      setMsg({ ok: true, text: `Queued · job ${res.jobId.slice(0, 8)}` });
      onUploaded?.();
    } catch (e) {
      setMsg({ ok: false, text: e.message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDrag(true);
        }}
        onDragLeave={() => setDrag(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDrag(false);
          send(e.dataTransfer.files?.[0]);
        }}
        onClick={() => input.current?.click()}
        className={`group flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed py-10 text-center transition ${
          drag ? 'border-signal bg-signal/5' : 'border-edge hover:border-cobalt/50'
        }`}
      >
        <input
          ref={input}
          type="file"
          className="hidden"
          accept=".pdf,.csv,.zip,image/*"
          onChange={(e) => send(e.target.files?.[0])}
        />
        <div className="font-mono text-[11px] uppercase tracking-[0.25em] text-mute">
          {busy ? 'transmitting…' : 'drop file or click'}
        </div>
        <div className="mt-2 text-sm text-ghost">PDF · IMAGE · CSV · ZIP — max 50&nbsp;MB</div>
      </div>
      {msg && (
        <div
          className={`mt-3 rounded-md border px-3 py-2 font-mono text-[11px] ${
            msg.ok
              ? 'border-signal/40 bg-signal/10 text-signal'
              : 'border-plasma/40 bg-plasma/10 text-plasma'
          }`}
        >
          {msg.text}
        </div>
      )}
    </div>
  );
}
