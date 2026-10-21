import type { ReactNode } from 'react';
import { RefreshCwIcon } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface Props {
  title: string;
  description: string;
  onRefresh: () => void;
  isFetching: boolean;
  children: ReactNode;
}

export function ContentLayout({ title, description, onRefresh, isFetching, children }: Props) {
  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-1">
          <h2 className="font-heading text-2xl font-semibold tracking-tight">{title}</h2>
          <p className="text-sm text-muted-foreground">{description}</p>
        </div>
        <Button variant="outline" onClick={onRefresh} disabled={isFetching} focusableWhenDisabled>
          <RefreshCwIcon data-icon="inline-start" className={isFetching ? 'animate-spin' : ''} />
          {isFetching ? 'Refreshing…' : 'Refresh'}
        </Button>
      </header>
      {children}
    </section>
  );
}
