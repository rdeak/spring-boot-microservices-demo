import { queryOptions, useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import type { QueryConfig } from '@/lib/react-query';
import type { Order } from '@/types/api';

const POLL_WHILE_PENDING_MS = 2000;

export const ordersQueryKey = ['orders'] as const;

export function getOrders({ limit }: { limit: number }): Promise<Order[]> {
  return api.get<Order[]>(`/api/orders?limit=${limit}`);
}

export function getOrdersQueryOptions({ limit = 50 }: { limit?: number } = {}) {
  return queryOptions({
    queryKey: [...ordersQueryKey, { limit }],
    queryFn: () => getOrders({ limit }),
    refetchInterval: (query) =>
      query.state.data?.some((order) => order.status === 'PENDING') ? POLL_WHILE_PENDING_MS : false,
  });
}

interface UseOrdersOptions {
  limit?: number;
  queryConfig?: QueryConfig<typeof getOrdersQueryOptions>;
}

export function useOrders({ limit, queryConfig }: UseOrdersOptions = {}) {
  return useQuery({ ...getOrdersQueryOptions({ limit }), ...queryConfig });
}
