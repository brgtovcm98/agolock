import http from 'k6/http';
import { check, sleep } from 'k6';
import { env, login } from './common.js';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 20 },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
  },
};

const BASE_URL = env('BASE_URL', 'http://localhost:8080');
const EMAIL = env('TEST_EMAIL', 'load-test@example.com');
const PASSWORD = env('TEST_PASSWORD', 'load-test-password');
let loggedIn = false;

export default function () {
  if (!loggedIn) {
    login(BASE_URL, EMAIL, PASSWORD);
    loggedIn = true;
  }
  const response = http.get(`${BASE_URL}/api/v1/search?q=${encodeURIComponent(env('SEARCH_Q', 'box'))}&limit=20`);
  check(response, { 'search 200': (r) => r.status === 200 });
  sleep(0.2);
}
