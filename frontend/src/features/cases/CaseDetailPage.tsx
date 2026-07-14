import { ArrowLeft } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../../api'
import { collectCursorPages } from '../../api/pagination'
import { ErrorNote } from '../../components/ErrorNote'
import { LoadingRows } from '../../components/LoadingState'
import { Panel } from '../../components/Panel'
import { ScoreReadout } from '../../components/ScoreReadout'
import { EDGE_LABELS } from '../../lib/labels'
import { useSession } from '../../auth/session'
import { useAsync } from '../../lib/useAsync'
import { useCursorPagination } from '../../lib/useCursorPagination'
import type { EdgeType } from '../../types/api'
import { AuditTrail } from './AuditTrail'
import { CaseActions } from './CaseActions'
import { EvidencePanel } from './EvidencePanel'
import { GraphExplorer } from './GraphExplorer'

const LEGEND: { type: EdgeType; color: string; dashed?: boolean }[] = [
  { type: 'REFERRED', color: 'var(--color-edge-referred)' },
  { type: 'SHARES_DEVICE', color: 'var(--color-edge-device)', dashed: true },
  { type: 'SHARES_IP_SUBNET', color: 'var(--color-edge-subnet)', dashed: true },
]

function GraphLegend({ present }: { present: ReadonlySet<EdgeType> }) {
  const shown = LEGEND.filter(({ type }) => present.has(type))
  if (shown.length === 0) return null
  return (
    <div className="flex flex-wrap items-center gap-3">
      {shown.map(({ type, color }) => (
        <span key={type} className="inline-flex items-center gap-1.5 text-xs text-muted">
          <span className="h-0.5 w-4" style={{ background: color }} />
          {EDGE_LABELS[type]}
        </span>
      ))}
    </div>
  )
}

export function CaseDetailPage() {
  const { tenantId } = useSession()
  const { id } = useParams()
  const caseId = Number(id)

  const caseQ = useAsync(() => api.getCase(tenantId, caseId), [tenantId, caseId])
  const graphQ = useAsync(() => api.getCaseGraph(tenantId, caseId), [tenantId, caseId])
  const auditQ = useCursorPagination(
    (cursor) => api.getCaseAudit(tenantId, caseId, cursor, 25),
    [tenantId, caseId],
  )
  const campaignsQ = useAsync(
    () => collectCursorPages((cursor) => api.listCampaigns(tenantId, cursor, 100)),
    [tenantId],
  )

  const c = caseQ.data
  const campaignName = c
    ? campaignsQ.data?.find((cp) => cp.campaignId === c.campaignId)?.name
    : undefined
  const presentEdgeTypes = new Set<EdgeType>(graphQ.data?.edges.map((e) => e.type))

  return (
    <div className="space-y-4">
      <Link
        to="/queue"
        className="inline-flex items-center gap-1.5 text-sm text-muted hover:text-text"
      >
        <ArrowLeft className="h-4 w-4" strokeWidth={1.75} /> Case Queue
      </Link>

      {caseQ.loading ? (
        <LoadingRows rows={3} />
      ) : caseQ.error || !c ? (
        <ErrorNote message={caseQ.error ?? 'Case not found'} />
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-border bg-surface p-5">
            <div>
              <div className="font-mono text-sm text-faint">
                #{c.id} · {c.referralCode}
              </div>
              <h1 className="font-display text-xl font-semibold tracking-tight">
                Referral payout review
              </h1>
            </div>
            <ScoreReadout score={c.score} />
          </div>

          <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.35fr)]">
            <div className="space-y-4">
              <EvidencePanel c={c} campaignName={campaignName} />
              <CaseActions
                c={c}
                onResolved={() => {
                  caseQ.reload()
                  auditQ.reload()
                }}
              />
              {!auditQ.loading && (
                <AuditTrail
                  entries={auditQ.items}
                  hasMore={Boolean(auditQ.nextCursor)}
                  loadingMore={auditQ.loadingMore}
                  onLoadMore={auditQ.loadMore}
                />
              )}
            </div>

            <Panel
              title="Referral cluster"
              actions={<GraphLegend present={presentEdgeTypes} />}
              bodyClassName="p-3"
            >
              {graphQ.loading ? (
                <LoadingRows rows={4} />
              ) : graphQ.error ? (
                <ErrorNote message={graphQ.error} />
              ) : (
                graphQ.data && <GraphExplorer graph={graphQ.data} />
              )}
            </Panel>
          </div>
        </>
      )}
    </div>
  )
}
