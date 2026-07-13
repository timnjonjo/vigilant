import { Panel } from '../../components/Panel'
import { RiskBadge } from '../../components/RiskBadge'
import { relativeTime } from '../../lib/format'
import type { RiskLevel } from '../../lib/risk'
import type { AlertSeverityLevel, OpsAlert } from '../../types/api'

// Alert severity shares the app's risk language exactly.
const LEVEL: Record<AlertSeverityLevel, RiskLevel> = {
  critical: 'critical',
  high: 'high',
  medium: 'medium',
  low: 'low',
}

export function AlertPanel({ alerts }: { alerts: OpsAlert[] }) {
  return (
    <Panel title="Alerts">
      {alerts.length === 0 ? (
        <p className="text-sm text-muted">No active alerts.</p>
      ) : (
        <ul className="space-y-3.5">
          {alerts.map((a) => (
            <li key={a.id} className="flex items-start gap-3">
              <RiskBadge level={LEVEL[a.severity]} />
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-x-2">
                  <span className="text-sm font-medium text-text">{a.title}</span>
                  <span className="text-xs text-faint">· {relativeTime(a.at)}</span>
                </div>
                <p className="text-sm text-muted">{a.detail}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}
