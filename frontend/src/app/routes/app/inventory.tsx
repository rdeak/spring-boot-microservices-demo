import { useState } from 'react';
import { ContentLayout } from '@/components/layouts/content-layout';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useReservations } from '@/features/inventory/api/get-reservations';
import { useStock } from '@/features/inventory/api/get-stock';
import { ReservationsTable } from '@/features/inventory/components/reservations-table';
import { StockTable } from '@/features/inventory/components/stock-table';

type Tab = 'stock' | 'reservations';

export function InventoryRoute() {
  const [tab, setTab] = useState<Tab>('stock');
  const stock = useStock();
  const reservations = useReservations();
  const isFetching = stock.isFetching || reservations.isFetching;

  return (
    <ContentLayout
      title="Inventory"
      description="Stock per SKU, and the reservation the saga made for each order."
      onRefresh={() => {
        void stock.refetch();
        void reservations.refetch();
      }}
      isFetching={isFetching}
    >
      <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)}>
        <TabsList aria-label="Inventory views">
          <TabsTrigger value="stock">Stock</TabsTrigger>
          <TabsTrigger value="reservations">Reservations</TabsTrigger>
        </TabsList>
        <TabsContent value="stock">
          <StockTable rows={stock.data} isLoading={stock.isLoading} error={stock.error} />
        </TabsContent>
        <TabsContent value="reservations">
          <ReservationsTable
            rows={reservations.data}
            isLoading={reservations.isLoading}
            error={reservations.error}
          />
        </TabsContent>
      </Tabs>
    </ContentLayout>
  );
}
