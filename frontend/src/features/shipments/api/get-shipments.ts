import { queryOptions, useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import type { QueryConfig } from '@/lib/react-query';
import type { Shipment } from '@/types/api';

export function getShipments({ limit }: { limit: number }): Promise<Shipment[]> {
  return api.get<Shipment[]>(`/api/shipping?limit=${limit}`);
}

export function getShipmentsQueryOptions({ limit = 50 }: { limit?: number } = {}) {
  return queryOptions({
    queryKey: ['shipments', { limit }],
    queryFn: () => getShipments({ limit }),
  });
}

interface UseShipmentsOptions {
  limit?: number;
  queryConfig?: QueryConfig<typeof getShipmentsQueryOptions>;
}

export function useShipments({ limit, queryConfig }: UseShipmentsOptions = {}) {
  return useQuery({ ...getShipmentsQueryOptions({ limit }), ...queryConfig });
}
