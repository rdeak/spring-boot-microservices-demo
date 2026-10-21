import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { server } from '@/testing/mocks/server';
import { findRows, renderApp } from '@/testing/test-utils';
import type { CreateOrderRequest, Order, Stock } from '@/types/api';
import { OrdersRoute } from '../orders';

const stock: Stock[] = [
  { sku: 'SKU-1', available: 100, reserved: 0 },
  { sku: 'SKU-2', available: 50, reserved: 0 },
];

function order(overrides: Partial<Order>): Order {
  return {
    id: '11111111-0000-0000-0000-000000000000',
    status: 'CONFIRMED',
    country: 'HR',
    items: [{ sku: 'SKU-1', quantity: 2 }],
    failureReason: null,
    createdAt: '2026-10-19T10:00:00Z',
    ...overrides,
  };
}

beforeEach(() => {
  server.use(http.get('/api/inventory', () => HttpResponse.json(stock)));
});

describe('OrdersRoute', () => {
  it('lists orders with status, items and failure reason', async () => {
    server.use(
      http.get('/api/orders', () =>
        HttpResponse.json([
          order({ id: 'aaaaaaaa-1', status: 'CONFIRMED' }),
          order({
            id: 'bbbbbbbb-2',
            status: 'FAILED',
            country: 'AQ',
            failureReason: "No carrier available for country 'AQ'",
          }),
        ]),
      ),
    );

    renderApp(<OrdersRoute />);

    const rows = await findRows('Orders, newest first', 2);
    expect(rows[0]).toHaveTextContent('aaaaaaaa');
    expect(rows[0]).toHaveTextContent('CONFIRMED');
    expect(rows[0]).toHaveTextContent('2 × SKU-1');
    expect(rows[1]).toHaveTextContent('FAILED');
    expect(rows[1]).toHaveTextContent("No carrier available for country 'AQ'");
  });

  it('shows an error row when the list cannot be loaded', async () => {
    server.use(
      http.get(
        '/api/orders',
        () => new HttpResponse('down', { status: 503, statusText: 'Service Unavailable' }),
      ),
    );

    renderApp(<OrdersRoute />);

    expect(await screen.findByRole('alert')).toHaveTextContent('503 Service Unavailable');
  });

  it('posts a new order with the chosen country, SKU and quantity, then refreshes the list', async () => {
    const user = userEvent.setup();
    const orders: Order[] = [];
    const posted: CreateOrderRequest[] = [];
    server.use(
      http.get('/api/orders', () => HttpResponse.json(orders)),
      http.post('/api/orders', async ({ request }) => {
        const body = (await request.json()) as CreateOrderRequest;
        posted.push(body);
        const created = order({ id: 'cccccccc-3', status: 'PENDING', ...body });
        orders.unshift(created);
        return HttpResponse.json(created, { status: 201 });
      }),
    );

    renderApp(<OrdersRoute />);
    await screen.findByText('No orders yet.');

    const quantity = await screen.findByLabelText('Quantity 1');
    await user.clear(quantity);
    await user.type(quantity, '3');
    await user.click(screen.getByRole('button', { name: 'Place order' }));

    expect(await screen.findByText('Order placed')).toBeInTheDocument();
    expect(posted).toEqual([{ country: 'HR', items: [{ sku: 'SKU-1', quantity: 3 }] }]);

    const rows = await findRows('Orders, newest first', 1);
    expect(rows[0]).toHaveTextContent('cccccccc');
    expect(rows[0]).toHaveTextContent('PENDING');
  });

  it('surfaces the server validation message when the order is rejected', async () => {
    const user = userEvent.setup();
    server.use(
      http.post('/api/orders', () =>
        HttpResponse.json({ detail: 'quantity must be between 1 and 10000' }, { status: 400 }),
      ),
    );

    renderApp(<OrdersRoute />);
    await screen.findByLabelText('Quantity 1');
    await user.click(screen.getByRole('button', { name: 'Place order' }));

    expect(await screen.findByText('Order rejected')).toBeInTheDocument();
    expect(screen.getByText('quantity must be between 1 and 10000')).toBeInTheDocument();
  });
});
