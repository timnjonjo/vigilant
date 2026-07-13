import { useState } from 'react'
import { api } from '../../api'
import { useSession } from '../../auth/session'
import { Button } from '../../components/Button'
import { Panel } from '../../components/Panel'
import { RiskBadge } from '../../components/RiskBadge'
import { riskLevelFromDecision } from '../../lib/risk'
import type { CaseAction, CaseView } from '../../types/api'

/**
 * Approve / Hold / Reject / Escalate. Actions update case state locally only —
 * host write-back is an open decision (spec §10) and explicitly out of scope.
 */
export function CaseActions({ c, onResolved }: { c: CaseView; onResolved: () => void }) {
  const session = useSession()
  const canAct = session.hasRole('fraud_analyst') || session.hasRole('tenant_admin')
  const [busy, setBusy] = useState<CaseAction | null>(null)

  async function act(action: CaseAction) {
    setBusy(action)
    try {
      await api.actOnCase(c.tenantId, c.id, action, session.username)
      onResolved()
    } finally {
      setBusy(null)
    }
  }

  if (c.status === 'RESOLVED') {
    return (
      <Panel title="Decision">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <RiskBadge
            level={riskLevelFromDecision(c.resolution ?? c.decision)}
            label={`Resolved · ${c.resolution}`}
          />
          <span className="text-muted">
            by <span className="font-mono text-text">{c.resolvedBy}</span>
          </span>
        </div>
      </Panel>
    )
  }

  if (!canAct) {
    // UI convenience only — the backend also rejects actions without the role.
    return (
      <Panel title="Decision">
        <p className="text-sm text-muted">You don’t have permission to action cases.</p>
      </Panel>
    )
  }

  return (
    <Panel title="Decision">
      <div className="grid grid-cols-2 gap-2">
        <Button variant="success" disabled={!!busy} onClick={() => act('APPROVE')}>
          Approve
        </Button>
        <Button variant="warn" disabled={!!busy} onClick={() => act('HOLD')}>
          Hold
        </Button>
        <Button variant="danger" disabled={!!busy} onClick={() => act('REJECT')}>
          Reject
        </Button>
        <Button variant="critical" disabled={!!busy} onClick={() => act('ESCALATE')}>
          Escalate
        </Button>
      </div>
      <p className="mt-2.5 text-xs text-muted">
        Approve or Reject closes the case; Hold and Escalate keep it in the queue.
      </p>
    </Panel>
  )
}
