import { HttpResponse, http } from 'msw';

export const handlers = [
  http.get('/api/orders', () => HttpResponse.json([])),
  http.get('/api/inventory', () => HttpResponse.json([])),
  http.get('/api/inventory/reservations', () => HttpResponse.json([])),
  http.get('/api/shipping', () => HttpResponse.json([])),
];
