import { useState } from 'react'
import { RiskBar } from '../../components/RiskBar'
import { cn } from '../../lib/cn'
import { formatKes } from '../../lib/format'
import type { TopCode } from '../../types/api'

type SortKey = 'volume' | 'riskScore' | 'blockedKes'

/** Top referral codes, sortable by volume, risk, or blocked value. */
export function TopCodesTable({ codes }: { codes: TopCode[] }) {
  const [sortKey, setSortKey] = useState<SortKey>('riskScore')
  const sorted = [...codes].sort((a, b) => b[sortKey] - a[sortKey])

  const Th = ({ k, label, right }: { k: SortKey; label: string; right?: boolean }) => (
    <th
      onClick={() => setSortKey(k)}
      className={cn(
        'cursor-pointer select-none px-3 py-2.5 font-medium hover:text-text',
        right && 'text-right',
        sortKey === k && 'text-text',
      )}
    >
      {label}
      {sortKey === k ? ' ↓' : ''}
    </th>
  )

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-xs uppercase tracking-wider text-muted">
            <th className="px-3 py-2.5 font-medium">Code</th>
            <Th k="volume" label="Volume" right />
            <Th k="riskScore" label="Risk" />
            <Th k="blockedKes" label="Blocked" right />
          </tr>
        </thead>
        <tbody>
          {sorted.map((c) => (
            <tr key={c.referralCode} className="border-b border-border/60">
              <td className="px-3 py-2.5 font-mono text-xs text-text">{c.referralCode}</td>
              <td className="px-3 py-2.5 text-right font-mono text-xs text-muted">{c.volume}</td>
              <td className="px-3 py-2.5">
                <RiskBar score={c.riskScore} />
              </td>
              <td className="px-3 py-2.5 text-right font-mono text-xs text-muted">
                {c.blockedKes > 0 ? formatKes(c.blockedKes, true) : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
