import type { DefaultOptions, UseMutationOptions } from '@tanstack/react-query';

export const queryConfig = {
  queries: { retry: 1, staleTime: 1000 },
} satisfies DefaultOptions;

type AnyFn = (...args: never[]) => unknown;
type ApiFn = (...args: never[]) => Promise<unknown>;

export type ApiFnReturnType<Fn extends ApiFn> = Awaited<ReturnType<Fn>>;

/** Everything `useQuery` accepts except what the feature's `*QueryOptions` already fixes. */
export type QueryConfig<Fn extends AnyFn> = Omit<ReturnType<Fn>, 'queryKey' | 'queryFn'>;

export type MutationConfig<Fn extends ApiFn> = UseMutationOptions<
  ApiFnReturnType<Fn>,
  Error,
  Parameters<Fn>[0]
>;
