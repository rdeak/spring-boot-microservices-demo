import { HttpResponse, http } from 'msw';
import { server } from '@/testing/mocks/server';
import { ApiError, api } from '../api-client';

describe('api client', () => {
  it('returns the parsed body on success', async () => {
    server.use(http.get('/api/inventory', () => HttpResponse.json([{ sku: 'SKU-1' }])));

    await expect(api.get('/api/inventory')).resolves.toEqual([{ sku: 'SKU-1' }]);
  });

  it('uses the problem detail from a 4xx response as the error message', async () => {
    server.use(
      http.post('/api/orders', () =>
        HttpResponse.json(
          { title: 'Bad Request', detail: 'items must not be empty' },
          { status: 400 },
        ),
      ),
    );

    const error = await api.post('/api/orders', {}).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(400);
    expect((error as ApiError).message).toBe('items must not be empty');
  });

  it('falls back to the status line when the body is not a problem detail', async () => {
    server.use(
      http.get(
        '/api/shipping',
        () => new HttpResponse('boom', { status: 502, statusText: 'Bad Gateway' }),
      ),
    );

    await expect(api.get('/api/shipping')).rejects.toThrow('502 Bad Gateway');
  });

  it('keeps caller headers that are passed as a Headers instance', async () => {
    let received: Headers | undefined;
    server.use(
      http.get('/api/orders', ({ request }) => {
        received = request.headers;
        return HttpResponse.json({});
      }),
    );

    await api.get('/api/orders', {
      headers: new Headers({ 'x-request-id': 'abc' }),
    });

    expect(received?.get('x-request-id')).toBe('abc');
    expect(received?.get('accept')).toBe('application/json');
  });

  it('posts JSON with the right headers', async () => {
    let received: { contentType: string | null; body: string } | undefined;
    server.use(
      http.post('/api/orders', async ({ request }) => {
        received = {
          contentType: request.headers.get('content-type'),
          body: await request.text(),
        };
        return HttpResponse.json({ id: '1' }, { status: 201 });
      }),
    );

    await api.post('/api/orders', { country: 'HR' });

    expect(received?.body).toBe('{"country":"HR"}');
    expect(received?.contentType).toBe('application/json');
  });
});
