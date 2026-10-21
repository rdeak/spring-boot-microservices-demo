import type { ReactNode } from 'react';
import { ExternalLinkIcon, RabbitIcon, WaypointsIcon } from 'lucide-react';
import { NavLink } from 'react-router';
import { buttonVariants } from '@/components/ui/button';
import { devTools } from '@/config/dev-tools';
import { paths } from '@/config/paths';
import { cn } from '@/lib/utils';

const NAV = [
  { to: paths.app.orders.getHref(), label: 'Orders' },
  { to: paths.app.inventory.getHref(), label: 'Inventory' },
  { to: paths.app.shipments.getHref(), label: 'Shipments' },
];

const DEV_TOOL_ICONS = { Jaeger: WaypointsIcon, RabbitMQ: RabbitIcon } as const;

export function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-svh bg-background">
      <header className="border-b">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
          <h1 className="font-heading text-lg font-semibold tracking-tight">Saga Console</h1>
          <nav aria-label="Main" className="flex items-center gap-1">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  cn(buttonVariants({ variant: isActive ? 'secondary' : 'ghost', size: 'sm' }))
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
          <nav aria-label="Dev tools" className="ml-auto flex items-center gap-2">
            <span className="hidden text-xs font-medium tracking-wide text-muted-foreground uppercase sm:inline">
              Dev tools
            </span>
            {devTools.map((tool) => {
              const Icon = DEV_TOOL_ICONS[tool.label];
              return (
                <a
                  key={tool.label}
                  href={tool.href}
                  target="_blank"
                  rel="noreferrer"
                  title={`${tool.description} (opens in a new tab)`}
                  className={cn(buttonVariants({ variant: 'outline', size: 'sm' }))}
                >
                  <Icon data-icon="inline-start" />
                  {tool.label}
                  <ExternalLinkIcon data-icon="inline-end" className="text-muted-foreground" />
                </a>
              );
            })}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">{children}</main>
    </div>
  );
}
