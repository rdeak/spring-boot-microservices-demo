import { queryOptions, useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import type { QueryConfig } from '@/lib/react-query';
import type { Stock } from '@/types/api';

export function getStock(): Promise<Stock[]> {
  return api.get<Stock[]>('/api/inventory');
}

export function getStockQueryOptions() {
  return queryOptions({
    queryKey: ['stock'],
    queryFn: getStock,
  });
}

interface UseStockOptions {
  queryConfig?: QueryConfig<typeof getStockQueryOptions>;
}

export function useStock({ queryConfig }: UseStockOptions = {}) {
  return useQuery({ ...getStockQueryOptions(), ...queryConfig });
}
