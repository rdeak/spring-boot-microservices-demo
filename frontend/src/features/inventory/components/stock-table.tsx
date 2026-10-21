import { DataTable, type Column } from '@/components/data-table';
import type { Stock } from '@/types/api';

const columns: Column<Stock>[] = [
  {
    key: 'sku',
    header: 'SKU',
    render: (row) => <code className="font-mono text-xs">{row.sku}</code>,
  },
  { key: 'available', header: 'Available', render: (row) => row.available },
  { key: 'reserved', header: 'Reserved', render: (row) => row.reserved },
];

interface Props {
  rows: Stock[] | undefined;
  isLoading: boolean;
  error: Error | null;
}

export function StockTable({ rows, isLoading, error }: Props) {
  return (
    <DataTable
      caption="Stock by SKU"
      columns={columns}
      rows={rows}
      rowKey={(row) => row.sku}
      isLoading={isLoading}
      error={error}
      emptyMessage="No stock rows."
    />
  );
}
