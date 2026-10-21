import { DataTable, type Column } from '@/components/data-table';
import { ShortId } from '@/components/short-id';
import { StatusBadge } from '@/components/status-badge';
import type { Reservation } from '@/types/api';
import { formatInstant, formatLines } from '@/utils/format';

const columns: Column<Reservation>[] = [
  {
    key: 'orderId',
    header: 'Order',
    render: (row) => <ShortId id={row.orderId} />,
  },
  { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
  { key: 'items', header: 'Items', render: (row) => formatLines(row.items) },
  { key: 'reason', header: 'Reason', render: (row) => row.reason ?? '' },
  {
    key: 'updatedAt',
    header: 'Updated',
    render: (row) => formatInstant(row.updatedAt),
  },
];

interface Props {
  rows: Reservation[] | undefined;
  isLoading: boolean;
  error: Error | null;
}

export function ReservationsTable({ rows, isLoading, error }: Props) {
  return (
    <DataTable
      caption="Reservations, newest first"
      columns={columns}
      rows={rows}
      rowKey={(row) => row.orderId}
      isLoading={isLoading}
      error={error}
      emptyMessage="No reservations yet."
    />
  );
}
