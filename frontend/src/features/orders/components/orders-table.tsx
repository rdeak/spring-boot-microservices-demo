import { DataTable, type Column } from '@/components/data-table';
import { ShortId } from '@/components/short-id';
import { StatusBadge } from '@/components/status-badge';
import type { Order } from '@/types/api';
import { formatInstant, formatLines } from '@/utils/format';

const columns: Column<Order>[] = [
  { key: 'id', header: 'Order', render: (order) => <ShortId id={order.id} /> },
  { key: 'status', header: 'Status', render: (order) => <StatusBadge status={order.status} /> },
  { key: 'country', header: 'Country', render: (order) => order.country },
  {
    key: 'items',
    header: 'Items',
    render: (order) => formatLines(order.items),
  },
  {
    key: 'failureReason',
    header: 'Failure reason',
    render: (order) => order.failureReason ?? '',
  },
  {
    key: 'createdAt',
    header: 'Created',
    render: (order) => formatInstant(order.createdAt),
  },
];

interface Props {
  rows: Order[] | undefined;
  isLoading: boolean;
  error: Error | null;
}

export function OrdersTable({ rows, isLoading, error }: Props) {
  return (
    <DataTable
      caption="Orders, newest first"
      columns={columns}
      rows={rows}
      rowKey={(order) => order.id}
      isLoading={isLoading}
      error={error}
      emptyMessage="No orders yet."
    />
  );
}
