import { useCallback, useEffect, useRef, useState, type DependencyList } from 'react'
import type { CursorPage } from '../types/api'

interface PagedCursor<T> {
  items: T[]
  page: number // 1-based current page
  knownPages: number // pages discovered so far (>= page); grows as you go forward
  hasNext: boolean
  hasPrev: boolean
  loading: boolean
  error?: string
  goTo(page: number): void
  next(): void
  prev(): void
  reload(): void
}

/**
 * Numbered paging over a keyset-cursor endpoint. The backend has no OFFSET and no
 * total count (that's what keeps deep pages O(1) — see perf/README), so pages are
 * walked sequentially: we remember the cursor that starts each page as it's
 * discovered, which lets an analyst jump back to any page already visited and step
 * forward one at a time. Changing the query (deps) resets to page 1.
 */
export function usePagedCursor<T>(
  loader: (cursor?: string) => Promise<CursorPage<T>>,
  deps: DependencyList,
): PagedCursor<T> {
  const loaderRef = useRef(loader)
  loaderRef.current = loader
  const generation = useRef(0)
  // cursors[i] is the cursor that starts page i+1; cursors[0] is undefined (page 1).
  const cursors = useRef<(string | undefined)[]>([undefined])
  // Highest page actually loaded this query — the numbered buttons cover 1..max,
  // and the Next arrow discovers the page beyond it. Only grows until deps reset.
  const maxPage = useRef(1)
  const [state, setState] = useState<{
    items: T[]
    page: number
    knownPages: number
    hasNext: boolean
    loading: boolean
    error?: string
  }>({ items: [], page: 1, knownPages: 1, hasNext: false, loading: true })

  const load = useCallback((pageIndex: number) => {
    const currentGeneration = ++generation.current
    setState((current) => ({ ...current, loading: true, error: undefined }))
    void loaderRef.current(cursors.current[pageIndex]).then(
      (result) => {
        if (generation.current !== currentGeneration) return
        if (result.nextCursor) {
          cursors.current[pageIndex + 1] = result.nextCursor
        } else {
          // No further pages from here: drop any stale forward cursors.
          cursors.current.length = pageIndex + 1
        }
        maxPage.current = Math.max(maxPage.current, pageIndex + 1)
        setState({
          items: result.items,
          page: pageIndex + 1,
          knownPages: maxPage.current,
          hasNext: Boolean(result.nextCursor),
          loading: false,
        })
      },
      (error: unknown) => {
        if (generation.current !== currentGeneration) return
        setState((current) => ({
          ...current,
          loading: false,
          error: error instanceof Error ? error.message : String(error),
        }))
      },
    )
  }, [])

  useEffect(() => {
    cursors.current = [undefined]
    maxPage.current = 1
    load(0)
    return () => {
      generation.current += 1
    }
    // The caller supplies the semantic query dependencies; loader is read from a ref.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  const goTo = useCallback(
    (page: number) => {
      const index = page - 1
      if (index >= 0 && index < cursors.current.length) load(index)
    },
    [load],
  )

  return {
    ...state,
    hasPrev: state.page > 1,
    goTo,
    next: () => {
      if (state.hasNext) load(state.page) // next page's index == current 1-based page
    },
    prev: () => {
      if (state.page > 1) load(state.page - 2)
    },
    reload: () => load(state.page - 1),
  }
}
