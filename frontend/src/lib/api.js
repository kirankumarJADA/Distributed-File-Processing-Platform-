const TOKEN_KEY = 'dfpp.token';

export const auth = {
  get token() {
    return localStorage.getItem(TOKEN_KEY);
  },
  set(token) {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem('dfpp.user');
  },
  get user() {
    try {
      return JSON.parse(localStorage.getItem('dfpp.user') || 'null');
    } catch {
      return null;
    }
  },
  setUser(u) {
    localStorage.setItem('dfpp.user', JSON.stringify(u));
  },
};

async function request(path, { method = 'GET', body, isForm = false } = {}) {
  const headers = {};
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`;
  if (!isForm) headers['Content-Type'] = 'application/json';

  const res = await fetch(path, {
    method,
    headers,
    body: isForm ? body : body ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error(data?.message || `Request failed (${res.status})`);
  }
  return data;
}

export const api = {
  register: (username, password) =>
    request('/api/auth/register', { method: 'POST', body: { username, password } }),
  login: (username, password) =>
    request('/api/auth/login', { method: 'POST', body: { username, password } }),
  uploadFile: (file) => {
    const fd = new FormData();
    fd.append('file', file);
    return request('/api/files', { method: 'POST', body: fd, isForm: true });
  },
  history: (page = 0, size = 50) => request(`/api/files?page=${page}&size=${size}`),
  file: (id) => request(`/api/files/${id}`),
  monitoring: () => request('/api/monitoring/summary'),
  adminStats: () => request('/api/admin/stats'),
};
