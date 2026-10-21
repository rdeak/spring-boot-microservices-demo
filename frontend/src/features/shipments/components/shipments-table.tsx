import { DataTable, type Column } from '@/components/data-table';
import { ShortId } from '@/components/short-id';
import { StatusBadge } from '@/components/status-badge';
import type { Shipment } from '@/types/api';
import { formatInstant } from '@/utils/format';

const columns: Column<Shipment>[] = [
  {
    key: 'orderId',
    header: 'Order',
    render: (row) => <ShortId id={row.orderId} />,
  },
  { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
  { key: 'carrier', header: 'Carrier', render: (row) => row.carrier ?? '' },
  {
    key: 'deliveryDate',
    header: 'Delivery',
    render: (row) => row.deliveryDate ?? '',
  },
  { key: 'reason', header: 'Reason', render: (row) => row.reason ?? '' },
  {
    key: 'createdAt',
    header: 'Created',
    render: (row) => formatInstant(row.createdAt),
  },
];

interface Props {
  rows: Shipment[] | undefined;
  isLoading: boolean;
  error: Error | null;
}

export function ShipmentsTable({ rows, isLoading, error }: Props) {
  return (
    <DataTable
      caption="Shipments, newest first"
      columns={columns}
      rows={rows}
      rowKey={(row) => row.orderId}
      isLoading={isLoading}
      error={error}
      emptyMessage="No shipments yet."
    />
  );
}
