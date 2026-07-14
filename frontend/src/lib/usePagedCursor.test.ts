import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { CursorPage } from '../types/api'
import { usePagedCursor } from './usePagedCursor'

// A three-page keyset endpoint: page 1 (cursor undefined) -> c1 -> c2 -> end.
function threePageLoader() {
  const pages: Record<string, CursorPage<string>> = {
    start: { items: ['a1', 'a2'], nextCursor: 'c1' },
    c1: { items: ['b1', 'b2'], nextCursor: 'c2' },
    c2: { items: ['c1item'], nextCursor: null },
  }
  return vi.fn((cursor?: string) => Promise.resolve(pages[cursor ?? 'start']))
}

describe('usePagedCursor', () => {
  it('starts on page 1 with no previous page', async () => {
    const loader = threePageLoader()
    const { result } = renderHook(() => usePagedCursor(loader, []))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.page).toBe(1)
    expect(result.current.items).toEqual(['a1', 'a2'])
    expect(result.current.hasPrev).toBe(false)
    expect(result.current.hasNext).toBe(true)
    expect(result.current.knownPages).toBe(1)
  })

  it('walks forward with next(), discovering page numbers as it goes', async () => {
    const loader = threePageLoader()
    const { result } = renderHook(() => usePagedCursor(loader, []))
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.next())
    await waitFor(() => expect(result.current.page).toBe(2))
    expect(result.current.items).toEqual(['b1', 'b2'])
    expect(result.current.hasPrev).toBe(true)
    expect(result.current.knownPages).toBe(2)

    act(() => result.current.next())
    await waitFor(() => expect(result.current.page).toBe(3))
    expect(result.current.items).toEqual(['c1item'])
    expect(result.current.hasNext).toBe(false) // last page
    expect(result.current.knownPages).toBe(3)
    // The last page uses the cursor from page 2, never an OFFSET.
    expect(loader).toHaveBeenCalledWith('c2')
  })

  it('goes back and jumps directly to a visited page', async () => {
    const loader = threePageLoader()
    const { result } = renderHook(() => usePagedCursor(loader, []))
    await waitFor(() => expect(result.current.loading).toBe(false))
    act(() => result.current.next())
    await waitFor(() => expect(result.current.page).toBe(2))
    act(() => result.current.next())
    await waitFor(() => expect(result.current.page).toBe(3))

    act(() => result.current.prev())
    await waitFor(() => expect(result.current.page).toBe(2))
    expect(result.current.items).toEqual(['b1', 'b2'])

    act(() => result.current.goTo(1))
    await waitFor(() => expect(result.current.page).toBe(1))
    expect(result.current.items).toEqual(['a1', 'a2'])
  })
})
