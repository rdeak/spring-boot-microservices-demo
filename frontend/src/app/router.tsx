import { useMemo } from 'react';
import { Navigate, RouterProvider, createBrowserRouter } from 'react-router';
import { paths } from '@/config/paths';
import { AppRoot } from './routes/app/root';

function createAppRouter() {
  return createBrowserRouter([
    {
      path: paths.home.path,
      element: <AppRoot />,
      children: [
        {
          index: true,
          element: <Navigate to={paths.app.orders.getHref()} replace />,
        },
        {
          path: paths.app.orders.path,
          lazy: async () => {
            const { OrdersRoute } = await import('./routes/app/orders');
            return { Component: OrdersRoute };
          },
        },
        {
          path: paths.app.inventory.path,
          lazy: async () => {
            const { InventoryRoute } = await import('./routes/app/inventory');
            return { Component: InventoryRoute };
          },
        },
        {
          path: paths.app.shipments.path,
          lazy: async () => {
            const { ShipmentsRoute } = await import('./routes/app/shipments');
            return { Component: ShipmentsRoute };
          },
        },
        {
          path: '*',
          element: <Navigate to={paths.app.orders.getHref()} replace />,
        },
      ],
    },
  ]);
}

export function AppRouter() {
  const router = useMemo(() => createAppRouter(), []);
  return <RouterProvider router={router} />;
}
