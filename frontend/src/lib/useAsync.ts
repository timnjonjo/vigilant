import { useEffect, useState } from 'react'

export interface AsyncState<T> {
  data?: T
  error?: string
  loading: boolean
}

/** Minimal data-fetching hook — swap for TanStack Query if the surface grows. */
export function useAsync<T>(
  fn: () => Promise<T>,
  deps: unknown[],
): AsyncState<T> & { reload: () => void } {
  const [state, setState] = useState<AsyncState<T>>({ loading: true })
  const [nonce, setNonce] = useState(0)

  useEffect(() => {
    let alive = true
    setState({ loading: true })
    fn()
      .then((data) => alive && setState({ data, loading: false }))
      .catch((e: unknown) =>
        alive && setState({ error: e instanceof Error ? e.message : String(e), loading: false }),
      )
    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])

  return { ...state, reload: () => setNonce((n) => n + 1) }
}
