import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import type { MutationConfig } from '@/lib/react-query';
import type { CreateOrderRequest, Order } from '@/types/api';
import { ordersQueryKey } from './get-orders';

export function createOrder(data: CreateOrderRequest): Promise<Order> {
  return api.post<Order>('/api/orders', data);
}

interface UseCreateOrderOptions {
  mutationConfig?: MutationConfig<typeof createOrder>;
}

export function useCreateOrder({ mutationConfig }: UseCreateOrderOptions = {}) {
  const queryClient = useQueryClient();
  const { onSuccess, ...restConfig } = mutationConfig ?? {};
  return useMutation({
    onSuccess: (...args) => {
      void queryClient.invalidateQueries({ queryKey: ordersQueryKey });
      return onSuccess?.(...args);
    },
    ...restConfig,
    mutationFn: createOrder,
  });
}
