import type { ReactNode } from 'react'
import { Panel } from '../../components/Panel'
import { ReasonCodeChip } from '../../components/ReasonCodeChip'
import { RiskBar } from '../../components/RiskBar'
import { relativeTime } from '../../lib/format'
import type { CaseView } from '../../types/api'

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 py-2.5">
      <dt className="text-xs uppercase tracking-wider text-faint">{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}

export function EvidencePanel({ c, campaignName }: { c: CaseView; campaignName?: string }) {
  return (
    <Panel title="Evidence">
      <dl className="divide-y divide-border/60">
        <Row label="Campaign">
          <span className="text-sm text-text" title={c.campaignId}>
            {campaignName ?? c.campaignId}
          </span>
        </Row>
        <Row label="Referral code">
          <span className="font-mono text-sm text-text">{c.referralCode}</span>
        </Row>
        <Row label="Referee">
          <span className="font-mono text-sm text-text">{c.refereeUserId}</span>
        </Row>
        <Row label="Score">
          <RiskBar score={c.score} />
        </Row>
        <Row label="Opened">
          <span className="font-mono text-xs text-muted">{relativeTime(c.openedAt)}</span>
        </Row>
      </dl>
      <div className="mt-3">
        <div className="mb-1.5 text-xs uppercase tracking-wider text-faint">Reason codes</div>
        <div className="flex flex-wrap gap-1.5">
          {c.reasonCodes.map((rc) => (
            <ReasonCodeChip key={rc} code={rc} />
          ))}
        </div>
      </div>
    </Panel>
  )
}
