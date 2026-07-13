/** Formatting helpers. Money and time are the analyst's units — keep them terse. */

const kes = new Intl.NumberFormat('en-KE')
const kesCompact = new Intl.NumberFormat('en-KE', { notation: 'compact', maximumFractionDigits: 2 })

export function formatKes(amount: number, compact = false): string {
  return `KES ${(compact ? kesCompact : kes).format(amount)}`
}

export function formatScore(score: number): string {
  return score.toFixed(2)
}

export function formatPct(value: number, withSign = false): string {
  const sign = withSign && value > 0 ? '+' : ''
  return `${sign}${value.toFixed(1)}%`
}

export function relativeTime(iso: string, now = Date.now()): string {
  const diffMs = now - new Date(iso).getTime()
  const mins = Math.round(diffMs / 60_000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hours = Math.round(mins / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.round(hours / 24)
  return `${days}d ago`
}
