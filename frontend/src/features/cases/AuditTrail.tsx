import { Panel } from '../../components/Panel'
import { relativeTime } from '../../lib/format'
import type { AuditEntry } from '../../types/api'

/** Who decided what, when — newest first. */
export function AuditTrail({ entries }: { entries: AuditEntry[] }) {
  if (entries.length === 0) return null
  const ordered = [...entries].reverse()
  return (
    <Panel title="Audit trail">
      <ol className="space-y-3.5">
        {ordered.map((entry) => (
          <li key={entry.id} className="flex gap-3 text-sm">
            <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-border-strong" />
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                <span className="font-medium text-text">{entry.action}</span>
                <span className="font-mono text-xs text-faint">{entry.actor}</span>
                <span className="text-xs text-faint">· {relativeTime(entry.at)}</span>
              </div>
              <p className="text-sm text-muted">{entry.note}</p>
            </div>
          </li>
        ))}
      </ol>
    </Panel>
  )
}
