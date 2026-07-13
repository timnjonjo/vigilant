import { cn } from '../lib/cn'
import { formatScore } from '../lib/format'
import { RISK_META, riskLevelFromScore } from '../lib/risk'

const SEGMENTS = 16

/**
 * The signature element: a segmented "signal bar" reading the score like an
 * instrument. Repeated across queue rows, the case header and ops metrics so the
 * whole app shares one risk gesture. This is the app's one loud thing — kept
 * precise, everything around it stays quiet.
 */
export function RiskBar({
  score,
  showValue = true,
  className,
}: {
  score: number
  showValue?: boolean
  className?: string
}) {
  const meta = RISK_META[riskLevelFromScore(score)]
  const filled = Math.max(1, Math.round(score * SEGMENTS))
  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      <div
        className="flex h-2.5 gap-px"
        role="meter"
        aria-valuenow={score}
        aria-valuemin={0}
        aria-valuemax={1}
        aria-label="Risk score"
      >
        {Array.from({ length: SEGMENTS }, (_, i) => (
          <span
            key={i}
            className={cn('w-1 rounded-[1px]', i < filled ? meta.dot : 'bg-border-strong')}
          />
        ))}
      </div>
      {showValue && (
        <span className={cn('font-mono text-sm tabular-nums', meta.text)}>{formatScore(score)}</span>
      )}
    </div>
  )
}
