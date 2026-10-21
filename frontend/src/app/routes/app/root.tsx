import { Outlet } from 'react-router';
import { AppLayout } from '@/components/layouts/app-layout';

export function AppRoot() {
  return (
    <AppLayout>
      <Outlet />
    </AppLayout>
  );
}
