import { ContentLayout } from '@/components/layouts/content-layout';
import { useShipments } from '@/features/shipments/api/get-shipments';
import { ShipmentsTable } from '@/features/shipments/components/shipments-table';

export function ShipmentsRoute() {
  const shipments = useShipments();
  return (
    <ContentLayout
      title="Shipments"
      description="One row per order that reached the shipping step, scheduled or failed."
      onRefresh={() => void shipments.refetch()}
      isFetching={shipments.isFetching}
    >
      <ShipmentsTable
        rows={shipments.data}
        isLoading={shipments.isLoading}
        error={shipments.error}
      />
    </ContentLayout>
  );
}
