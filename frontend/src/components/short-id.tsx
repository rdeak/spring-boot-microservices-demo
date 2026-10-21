export function ShortId({ id }: { id: string }) {
  return (
    <code title={id} className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-xs">
      {id.slice(0, 8)}
    </code>
  );
}
