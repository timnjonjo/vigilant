import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '../lib/cn'

/**
 * Numbered page navigation for the keyset-cursor lists: previous, a button per
 * discovered page, and next. Page numbers grow as the analyst steps forward (the
 * endpoint is cursor-based with no total count), and any already-visited page can
 * be jumped to directly.
 */
export function Pagination({
  page,
  knownPages,
  hasNext,
  hasPrev,
  loading = false,
  onGoTo,
  onPrev,
  onNext,
}: {
  page: number
  knownPages: number
  hasNext: boolean
  hasPrev: boolean
  loading?: boolean
  onGoTo: (page: number) => void
  onPrev: () => void
  onNext: () => void
}) {
  if (knownPages <= 1 && !hasNext) return null
  const pages = Array.from({ length: knownPages }, (_, i) => i + 1)
  const arrow = 'inline-flex h-8 w-8 items-center justify-center rounded-md border border-border text-muted hover:bg-surface-2 hover:text-text disabled:pointer-events-none disabled:opacity-40'

  return (
    <nav
      aria-label="Pagination"
      className="flex items-center justify-center gap-1 border-t border-border p-3"
    >
      <button type="button" className={arrow} onClick={onPrev} disabled={!hasPrev || loading} aria-label="Previous page">
        <ChevronLeft className="h-4 w-4" strokeWidth={1.75} />
      </button>
      {pages.map((p) => (
        <button
          key={p}
          type="button"
          onClick={() => onGoTo(p)}
          disabled={loading}
          aria-current={p === page ? 'page' : undefined}
          className={cn(
            'inline-flex h-8 min-w-8 items-center justify-center rounded-md border px-2 text-sm font-medium tabular-nums transition-colors disabled:pointer-events-none disabled:opacity-40',
            p === page
              ? 'border-border-strong bg-surface-2 text-text'
              : 'border-transparent text-muted hover:bg-surface-2 hover:text-text',
          )}
        >
          {p}
        </button>
      ))}
      <button type="button" className={arrow} onClick={onNext} disabled={!hasNext || loading} aria-label="Next page">
        <ChevronRight className="h-4 w-4" strokeWidth={1.75} />
      </button>
    </nav>
  )
}
