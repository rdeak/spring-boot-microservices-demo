export const paths = {
  home: {
    path: '/',
    getHref: () => '/',
  },
  app: {
    orders: {
      path: 'orders',
      getHref: () => '/orders',
    },
    inventory: {
      path: 'inventory',
      getHref: () => '/inventory',
    },
    shipments: {
      path: 'shipments',
      getHref: () => '/shipments',
    },
  },
} as const;
