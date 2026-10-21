import { useState, type SubmitEvent } from 'react';
import { PlusIcon, Trash2Icon } from 'lucide-react';
import { toast } from 'sonner';
import { SelectField, type SelectOption } from '@/components/select-field';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Field, FieldLabel } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import type { Stock } from '@/types/api';
import { useCreateOrder } from '../api/create-order';

const COUNTRIES: SelectOption[] = [
  { value: 'HR', label: 'HR · Croatia' },
  { value: 'DE', label: 'DE · Germany' },
  { value: 'US', label: 'US · United States' },
  { value: 'AQ', label: 'AQ · Antarctica (no carrier)' },
];

interface LineDraft {
  key: number;
  sku: string | null;
  quantity: number | null;
}

let nextKey = 1;

function emptyLine(): LineDraft {
  return { key: nextKey++, sku: null, quantity: 1 };
}

interface Props {
  /** Stock rows to offer as SKUs; owned by the inventory feature, so the route passes them in. */
  stock: Stock[] | undefined;
  isStockLoading: boolean;
}

export function CreateOrderForm({ stock, isStockLoading }: Props) {
  const [country, setCountry] = useState<string | null>('HR');
  const [lines, setLines] = useState<LineDraft[]>(() => [emptyLine()]);
  const createOrderMutation = useCreateOrder({
    mutationConfig: {
      onSuccess: (order) => {
        toast.success('Order placed', { description: order.id });
        setLines([emptyLine()]);
      },
      onError: (error) => {
        toast.error('Order rejected', { description: error.message });
      },
    },
  });

  const skuOptions: SelectOption[] = (stock ?? []).map((row) => ({
    value: row.sku,
    label: `${row.sku} (${row.available} available)`,
  }));

  const firstSku = skuOptions[0]?.value ?? null;
  const skuOf = (line: LineDraft) => line.sku ?? firstSku;

  function updateLine(key: number, patch: Partial<LineDraft>) {
    setLines((current) => current.map((line) => (line.key === key ? { ...line, ...patch } : line)));
  }

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!country) {
      toast.error('Choose a country');
      return;
    }
    const items = lines.flatMap((line) => {
      const sku = skuOf(line);
      return sku && line.quantity ? [{ sku, quantity: line.quantity }] : [];
    });
    if (items.length === 0) {
      toast.error('Add at least one line with a SKU and quantity');
      return;
    }
    createOrderMutation.mutate({ country, items });
  }

  return (
    <Card>
      <form onSubmit={submit} aria-label="New order" className="contents">
        <CardHeader>
          <CardTitle>New order</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="max-w-sm">
            <SelectField
              label="Country"
              name="country"
              value={country}
              options={COUNTRIES}
              placeholder="Choose a country"
              onChange={setCountry}
            />
          </div>
          {lines.map((line, index) => (
            <div key={line.key} className="flex flex-wrap items-end gap-3">
              <div className="min-w-56 flex-1">
                <SelectField
                  label={`SKU ${index + 1}`}
                  name={`sku-${index}`}
                  value={skuOf(line)}
                  options={skuOptions}
                  placeholder={isStockLoading ? 'Loading stock…' : 'Choose a SKU'}
                  onChange={(sku) => updateLine(line.key, { sku })}
                  disabled={skuOptions.length === 0}
                />
              </div>
              <Field className="w-32">
                <FieldLabel htmlFor={`quantity-${index}`}>Quantity {index + 1}</FieldLabel>
                <Input
                  id={`quantity-${index}`}
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={10000}
                  value={line.quantity ?? ''}
                  onChange={(event) =>
                    updateLine(line.key, {
                      quantity: event.target.value === '' ? null : Number(event.target.value),
                    })
                  }
                />
              </Field>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={`Remove line ${index + 1}`}
                disabled={lines.length === 1}
                onClick={() =>
                  setLines((current) => current.filter((candidate) => candidate.key !== line.key))
                }
              >
                <Trash2Icon />
              </Button>
            </div>
          ))}
        </CardContent>
        <CardFooter className="gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => setLines((current) => [...current, emptyLine()])}
          >
            <PlusIcon data-icon="inline-start" />
            Add line
          </Button>
          <Button type="submit" disabled={createOrderMutation.isPending} focusableWhenDisabled>
            {createOrderMutation.isPending ? 'Placing…' : 'Place order'}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
