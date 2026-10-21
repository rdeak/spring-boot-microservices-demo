import { Badge } from '@/components/ui/badge';

type Tone = 'default' | 'secondary' | 'destructive' | 'outline';

/** Every status the three services report, mapped to a badge tone. */
const TONES: Record<string, Tone> = {
  PENDING: 'secondary',
  CONFIRMED: 'default',
  FAILED: 'destructive',
  RESERVED: 'default',
  REJECTED: 'destructive',
  RELEASED: 'outline',
  SCHEDULED: 'default',
};

export function StatusBadge({ status }: { status: string }) {
  return <Badge variant={TONES[status] ?? 'outline'}>{status}</Badge>;
}
