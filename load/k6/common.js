import http from 'k6/http';
import { check } from 'k6';

export function login(baseUrl, email, password) {
  const csrf = http.get(`${baseUrl}/api/v1/auth/csrf`);
  const token = csrf.json('token');
  const response = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': token,
      },
    },
  );
  check(response, { 'login 200': (r) => r.status === 200 });
  return response;
}

export function env(name, fallback) {
  return __ENV[name] || fallback;
}
