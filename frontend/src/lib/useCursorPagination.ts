import { useEffect, useRef, useState, type DependencyList } from 'react'
import type { CursorPage } from '../types/api'

interface CursorPagination<T> {
  items: T[]
  nextCursor: string | null
  loading: boolean
  loadingMore: boolean
  error?: string
  loadMore(): void
  reload(): void
}

/** Accumulates cursor pages and invalidates the chain whenever dependencies change. */
export function useCursorPagination<T>(
  loader: (cursor?: string) => Promise<CursorPage<T>>,
  deps: DependencyList,
): CursorPagination<T> {
  const loaderRef = useRef(loader)
  loaderRef.current = loader
  const generation = useRef(0)
  const [state, setState] = useState<Omit<CursorPagination<T>, 'loadMore' | 'reload'>>({
    items: [],
    nextCursor: null,
    loading: true,
    loadingMore: false,
  })

  function request(cursor: string | undefined, append: boolean) {
    const currentGeneration = append ? generation.current : ++generation.current
    setState((current) => ({
      items: append ? current.items : [],
      nextCursor: append ? current.nextCursor : null,
      loading: !append,
      loadingMore: append,
    }))
    void loaderRef.current(cursor).then(
      (page) => {
        if (generation.current !== currentGeneration) return
        setState((current) => ({
          items: append ? [...current.items, ...page.items] : page.items,
          nextCursor: page.nextCursor,
          loading: false,
          loadingMore: false,
        }))
      },
      (error: unknown) => {
        if (generation.current !== currentGeneration) return
        setState((current) => ({
          ...current,
          loading: false,
          loadingMore: false,
          error: error instanceof Error ? error.message : String(error),
        }))
      },
    )
  }

  useEffect(() => {
    request(undefined, false)
    return () => {
      generation.current += 1
    }
    // The caller supplies the semantic query dependencies; loader is read from a ref.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return {
    ...state,
    loadMore: () => {
      if (state.nextCursor && !state.loadingMore) request(state.nextCursor, true)
    },
    reload: () => request(undefined, false),
  }
}
