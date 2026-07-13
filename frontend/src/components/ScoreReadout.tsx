import { cn } from '../lib/cn'
import { formatScore } from '../lib/format'
import { RISK_META, riskLevelFromScore } from '../lib/risk'
import { RiskBadge } from './RiskBadge'
import { RiskBar } from './RiskBar'

/** Enlarged score treatment for the case header. */
export function ScoreReadout({ score }: { score: number }) {
  const level = riskLevelFromScore(score)
  const meta = RISK_META[level]
  return (
    <div className="flex items-center gap-4">
      <span className={cn('font-mono text-4xl font-semibold tabular-nums', meta.text)}>
        {formatScore(score)}
      </span>
      <div className="flex flex-col items-start gap-1.5">
        <RiskBadge level={level} label={`${meta.label} risk`} />
        <RiskBar score={score} showValue={false} />
      </div>
    </div>
  )
}
