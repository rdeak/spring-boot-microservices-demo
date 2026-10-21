export function formatInstant(iso: string | null | undefined): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString();
}

interface Line {
  sku: string;
  quantity: number;
}

export function formatLines(lines: readonly Line[]): string {
  return lines.map((line) => `${line.quantity} × ${line.sku}`).join(', ');
}
