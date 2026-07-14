import type { CursorPage } from '../types/api'

/** Collects a normally-small config list for selectors while each HTTP call remains bounded. */
export async function collectCursorPages<T>(
  loader: (cursor?: string) => Promise<CursorPage<T>>,
): Promise<T[]> {
  const items: T[] = []
  let cursor: string | undefined
  do {
    const page = await loader(cursor)
    items.push(...page.items)
    cursor = page.nextCursor ?? undefined
  } while (cursor)
  return items
}
