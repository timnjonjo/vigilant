import { cn } from '../lib/cn'
import { RISK_META, type RiskLevel } from '../lib/risk'

/** The consistent risk pill: a coloured dot + label. Used for status and severity. */
export function RiskBadge({ level, label }: { level: RiskLevel; label?: string }) {
  const meta = RISK_META[level]
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs font-medium',
        meta.bgSoft,
        meta.border,
        meta.text,
      )}
    >
      <span className={cn('h-1.5 w-1.5 rounded-full', meta.dot)} />
      {label ?? meta.label}
    </span>
  )
}
