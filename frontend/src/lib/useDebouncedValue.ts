import { useEffect, useState } from 'react'

/**
 * Returns a copy of {@code value} that only updates after it has stopped changing
 * for {@code delayMs}. Used to keep a search input responsive while firing the
 * actual query (and resetting pagination) at most once the analyst pauses typing.
 */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}
