import { useState, type ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from '@/components/ui/sonner';
import { queryConfig } from '@/lib/react-query';

interface Props {
  children: ReactNode;
  /** Tests pass their own client (no retries, no stale time). */
  queryClient?: QueryClient;
}

export function AppProvider({ children, queryClient }: Props) {
  const [client] = useState(() => queryClient ?? new QueryClient({ defaultOptions: queryConfig }));
  return (
    <QueryClientProvider client={client}>
      {children}
      <Toaster position="bottom-right" />
    </QueryClientProvider>
  );
}
