import React, { useState } from 'react';
import { auth } from './lib/api';
import Login from './components/Login';
import Dashboard from './components/Dashboard';

export default function App() {
  const [user, setUser] = useState(() => (auth.token ? auth.user : null));

  if (!user) {
    return <Login onAuthed={setUser} />;
  }

  return (
    <Dashboard
      user={user}
      onLogout={() => {
        auth.clear();
        setUser(null);
      }}
    />
  );
}
