import { useNavigate } from 'react-router-dom'
import { RiskBadge } from '../../components/RiskBadge'
import { RiskBar } from '../../components/RiskBar'
import { ReasonCodeChip } from '../../components/ReasonCodeChip'
import { relativeTime } from '../../lib/format'
import { riskLevelFromDecision } from '../../lib/risk'
import type { CaseView } from '../../types/api'

export function CaseQueueTable({ cases }: { cases: CaseView[] }) {
  const navigate = useNavigate()
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-xs uppercase tracking-wider text-muted">
            <th className="px-4 py-2.5 font-medium">Signal</th>
            <th className="px-4 py-2.5 font-medium">Case</th>
            <th className="px-4 py-2.5 font-medium">Referee</th>
            <th className="px-4 py-2.5 font-medium">Reasons</th>
            <th className="px-4 py-2.5 font-medium">Status</th>
            <th className="px-4 py-2.5 text-right font-medium">Age</th>
          </tr>
        </thead>
        <tbody>
          {cases.map((c) => (
            <tr
              key={c.id}
              onClick={() => navigate(`/cases/${c.id}`)}
              className="cursor-pointer border-b border-border/60 transition-colors hover:bg-surface-2"
            >
              <td className="px-4 py-3">
                <RiskBar score={c.score} />
              </td>
              <td className="px-4 py-3">
                <div className="font-mono text-xs leading-tight">
                  <div className="text-text">#{c.id}</div>
                  <div className="text-faint">{c.referralCode}</div>
                </div>
              </td>
              <td className="px-4 py-3 font-mono text-xs text-muted">{c.refereeUserId}</td>
              <td className="px-4 py-3">
                <div className="flex flex-wrap gap-1">
                  {c.reasonCodes.map((rc) => (
                    <ReasonCodeChip key={rc} code={rc} />
                  ))}
                </div>
              </td>
              <td className="px-4 py-3">
                <RiskBadge
                  level={riskLevelFromDecision(c.decision)}
                  label={c.status === 'RESOLVED' ? `Resolved · ${c.resolution}` : c.decision}
                />
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-right font-mono text-xs text-muted">
                {relativeTime(c.openedAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
