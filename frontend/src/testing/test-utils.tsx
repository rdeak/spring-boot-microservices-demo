import type { ReactElement } from 'react';
import { QueryClient } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { render, screen, waitFor, within } from '@testing-library/react';
import { AppProvider } from '@/app/provider';

export function renderApp(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  });
  return render(
    <AppProvider queryClient={queryClient}>
      <MemoryRouter>{ui}</MemoryRouter>
    </AppProvider>,
  );
}

export async function findRows(tableName: string, count: number): Promise<HTMLElement[]> {
  const table = await screen.findByRole('table', { name: tableName });
  let rows: HTMLElement[] = [];
  await waitFor(() => {
    rows = within(table).getAllByRole('row').slice(1);
    if (rows.length !== count) {
      throw new Error(`expected ${count} data rows in "${tableName}", found ${rows.length}`);
    }
  });
  return rows;
}
