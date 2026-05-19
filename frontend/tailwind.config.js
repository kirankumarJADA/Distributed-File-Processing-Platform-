/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      fontFamily: {
        display: ['"Space Mono"', 'ui-monospace', 'monospace'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
        sans: ['"Sora"', 'system-ui', 'sans-serif'],
      },
      colors: {
        ink: '#070a0f',
        panel: '#0d1219',
        panel2: '#111824',
        edge: '#1d2735',
        mute: '#5b6b80',
        ghost: '#8a9bb3',
        wire: '#e6edf5',
        signal: '#3ef0c4',
        plasma: '#ff5d73',
        amber: '#ffb454',
        cobalt: '#5b8def',
      },
      boxShadow: {
        glow: '0 0 0 1px rgba(62,240,196,0.25), 0 0 28px -6px rgba(62,240,196,0.35)',
        plasma: '0 0 0 1px rgba(255,93,115,0.3), 0 0 24px -6px rgba(255,93,115,0.4)',
      },
      keyframes: {
        sweep: { '0%': { transform: 'translateX(-100%)' }, '100%': { transform: 'translateX(220%)' } },
        rise: { '0%': { opacity: 0, transform: 'translateY(10px)' }, '100%': { opacity: 1, transform: 'translateY(0)' } },
        pulseDot: { '0%,100%': { opacity: 1 }, '50%': { opacity: 0.25 } },
      },
      animation: {
        sweep: 'sweep 2.4s linear infinite',
        rise: 'rise 0.5s ease-out both',
        pulseDot: 'pulseDot 1.4s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};
