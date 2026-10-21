import type { ReactNode } from 'react';
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';

export interface Column<Row> {
  key: string;
  header: string;
  render: (row: Row) => ReactNode;
}

interface Props<Row> {
  columns: Column<Row>[];
  rows: Row[] | undefined;
  rowKey: (row: Row) => string;
  isLoading: boolean;
  error: Error | null;
  emptyMessage: string;
  caption: string;
}

export function DataTable<Row>({
  columns,
  rows,
  rowKey,
  isLoading,
  error,
  emptyMessage,
  caption,
}: Props<Row>) {
  return (
    <div className="rounded-xl ring-1 ring-foreground/10">
      <Table>
        <TableCaption className="sr-only">{caption}</TableCaption>
        <TableHeader>
          <TableRow>
            {columns.map((column) => (
              <TableHead key={column.key} scope="col">
                {column.header}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {error ? (
            <TableRow>
              <TableCell colSpan={columns.length} role="alert" className="text-destructive">
                Could not load: {error.message}
              </TableCell>
            </TableRow>
          ) : isLoading ? (
            <TableRow>
              <TableCell colSpan={columns.length} className="text-muted-foreground">
                Loading…
              </TableCell>
            </TableRow>
          ) : !rows || rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={columns.length} className="text-muted-foreground">
                {emptyMessage}
              </TableCell>
            </TableRow>
          ) : (
            rows.map((row) => (
              <TableRow key={rowKey(row)}>
                {columns.map((column) => (
                  <TableCell key={column.key}>{column.render(row)}</TableCell>
                ))}
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  );
}
