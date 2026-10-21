import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { server } from '@/testing/mocks/server';
import { findRows, renderApp } from '@/testing/test-utils';
import type { Reservation, Stock } from '@/types/api';
import { InventoryRoute } from '../inventory';

const stock: Stock[] = [
  { sku: 'SKU-1', available: 98, reserved: 2 },
  { sku: 'SKU-LOW', available: 1, reserved: 0 },
];

const reservations: Reservation[] = [
  {
    orderId: 'dddddddd-0000-0000-0000-000000000000',
    status: 'REJECTED',
    reason: 'Insufficient stock for SKU-LOW: requested 5, available 1',
    items: [],
    createdAt: '2026-10-19T10:01:00Z',
    updatedAt: '2026-10-19T10:01:00Z',
  },
  {
    orderId: 'eeeeeeee-0000-0000-0000-000000000000',
    status: 'RESERVED',
    reason: null,
    items: [{ sku: 'SKU-1', quantity: 2 }],
    createdAt: '2026-10-19T10:00:00Z',
    updatedAt: '2026-10-19T10:00:00Z',
  },
];

describe('InventoryRoute', () => {
  it('shows stock levels first and reservations on the second tab', async () => {
    const user = userEvent.setup();
    server.use(
      http.get('/api/inventory', () => HttpResponse.json(stock)),
      http.get('/api/inventory/reservations', () => HttpResponse.json(reservations)),
    );

    renderApp(<InventoryRoute />);

    const stockRows = await findRows('Stock by SKU', 2);
    expect(stockRows[0]).toHaveTextContent('SKU-1');
    expect(stockRows[0]).toHaveTextContent('98');
    expect(stockRows[0]).toHaveTextContent('2');

    await user.click(screen.getByRole('tab', { name: 'Reservations' }));

    const rows = await findRows('Reservations, newest first', 2);
    expect(rows[0]).toHaveTextContent('REJECTED');
    expect(rows[0]).toHaveTextContent('Insufficient stock for SKU-LOW');
    expect(rows[1]).toHaveTextContent('RESERVED');
    expect(rows[1]).toHaveTextContent('2 × SKU-1');
  });
});
