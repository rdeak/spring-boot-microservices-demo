import { ContentLayout } from '@/components/layouts/content-layout';
import { useStock } from '@/features/inventory/api/get-stock';
import { useOrders } from '@/features/orders/api/get-orders';
import { CreateOrderForm } from '@/features/orders/components/create-order-form';
import { OrdersTable } from '@/features/orders/components/orders-table';

export function OrdersRoute() {
  const orders = useOrders();
  const stock = useStock();
  const pending = orders.data?.filter((order) => order.status === 'PENDING').length ?? 0;
  return (
    <ContentLayout
      title="Orders"
      description={
        pending > 0
          ? `Saga in progress for ${pending} order${pending === 1 ? '' : 's'}; refreshing every 2 s.`
          : 'Place an order and watch the saga confirm or fail it.'
      }
      onRefresh={() => void orders.refetch()}
      isFetching={orders.isFetching}
    >
      <CreateOrderForm stock={stock.data} isStockLoading={stock.isLoading} />
      <OrdersTable rows={orders.data} isLoading={orders.isLoading} error={orders.error} />
    </ContentLayout>
  );
}
