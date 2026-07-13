import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '../lib/cn'
import { formatPct } from '../lib/format'

/**
 * Headline metric. Deliberately monochrome — trend arrows are neutral, since a
 * trend's direction isn't a risk signal. Colour stays reserved for severity.
 */
export function StatCard({
  label,
  value,
  trendPct,
  hint,
}: {
  label: string
  value: ReactNode
  trendPct?: number
  hint?: string
}) {
  const Arrow = trendPct == null || trendPct === 0 ? Minus : trendPct > 0 ? ArrowUpRight : ArrowDownRight
  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <div className="text-xs font-semibold uppercase tracking-wider text-muted">{label}</div>
      <div className="mt-2 font-mono text-2xl font-semibold tabular-nums text-text">{value}</div>
      <div className="mt-1.5 flex items-center gap-1.5 text-xs text-muted">
        {trendPct != null && (
          <span className="inline-flex items-center gap-0.5">
            <Arrow className="h-3.5 w-3.5" strokeWidth={2} />
            <span className="font-mono tabular-nums">{formatPct(Math.abs(trendPct))}</span>
          </span>
        )}
        {hint && <span className={cn(trendPct != null && 'before:mr-1.5 before:content-["·"]')}>{hint}</span>}
      </div>
    </div>
  )
}
