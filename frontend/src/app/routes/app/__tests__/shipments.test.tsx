import { screen } from '@testing-library/react';
import { HttpResponse, http } from 'msw';
import { server } from '@/testing/mocks/server';
import { findRows, renderApp } from '@/testing/test-utils';
import type { Shipment } from '@/types/api';
import { ShipmentsRoute } from '../shipments';

const shipments: Shipment[] = [
  {
    orderId: 'ffffffff-0000-0000-0000-000000000000',
    status: 'FAILED',
    carrier: null,
    deliveryDate: null,
    reason: "No carrier available for country 'AQ'",
    createdAt: '2026-10-19T10:01:00Z',
  },
  {
    orderId: '99999999-0000-0000-0000-000000000000',
    status: 'SCHEDULED',
    carrier: 'GLS Croatia',
    deliveryDate: '2026-10-21',
    reason: null,
    createdAt: '2026-10-19T10:00:00Z',
  },
];

describe('ShipmentsRoute', () => {
  it('lists shipments with carrier, delivery date and failure reason', async () => {
    const limits: (string | null)[] = [];
    server.use(
      http.get('/api/shipping', ({ request }) => {
        limits.push(new URL(request.url).searchParams.get('limit'));
        return HttpResponse.json(shipments);
      }),
    );

    renderApp(<ShipmentsRoute />);

    const rows = await findRows('Shipments, newest first', 2);
    expect(rows[0]).toHaveTextContent('FAILED');
    expect(rows[0]).toHaveTextContent("No carrier available for country 'AQ'");
    expect(rows[1]).toHaveTextContent('SCHEDULED');
    expect(rows[1]).toHaveTextContent('GLS Croatia');
    expect(rows[1]).toHaveTextContent('2026-10-21');
    expect(limits).toEqual(['50']);
  });

  it('shows the empty state when there are no shipments', async () => {
    renderApp(<ShipmentsRoute />);

    expect(await screen.findByText('No shipments yet.')).toBeInTheDocument();
  });
});
