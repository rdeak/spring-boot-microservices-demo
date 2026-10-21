import { queryOptions, useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import type { QueryConfig } from '@/lib/react-query';
import type { Reservation } from '@/types/api';

export function getReservations({ limit }: { limit: number }): Promise<Reservation[]> {
  return api.get<Reservation[]>(`/api/inventory/reservations?limit=${limit}`);
}

export function getReservationsQueryOptions({ limit = 50 }: { limit?: number } = {}) {
  return queryOptions({
    queryKey: ['reservations', { limit }],
    queryFn: () => getReservations({ limit }),
  });
}

interface UseReservationsOptions {
  limit?: number;
  queryConfig?: QueryConfig<typeof getReservationsQueryOptions>;
}

export function useReservations({ limit, queryConfig }: UseReservationsOptions = {}) {
  return useQuery({ ...getReservationsQueryOptions({ limit }), ...queryConfig });
}
