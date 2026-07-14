import { Megaphone, Pencil, Plus } from 'lucide-react'
import { useState } from 'react'
import { api } from '../../api'
import { useSession } from '../../auth/session'
import { Button } from '../../components/Button'
import { EmptyState } from '../../components/EmptyState'
import { ErrorNote } from '../../components/ErrorNote'
import { LoadingRows } from '../../components/LoadingState'
import { Panel } from '../../components/Panel'
import { formatKes } from '../../lib/format'
import { useCursorPagination } from '../../lib/useCursorPagination'
import type { Campaign, CampaignInput, CampaignStatus } from '../../types/api'
import { CampaignForm } from './CampaignForm'

const STATUS_STYLES: Record<CampaignStatus, string> = {
  ACTIVE: 'text-risk-low border-risk-low/40 bg-risk-low/10',
  DRAFT: 'text-muted border-border-strong bg-surface-2',
  PAUSED: 'text-risk-medium border-risk-medium/40 bg-risk-medium/10',
  ENDED: 'text-faint border-border bg-surface-2',
}

function StatusPill({ status }: { status: CampaignStatus }) {
  return (
    <span className={`inline-flex rounded-full border px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[status]}`}>
      {status}
    </span>
  )
}

const CRITERIA_LABEL: Record<Campaign['conversionCriteria'], string> = {
  FIRST_DEPOSIT: 'First deposit',
  N_DAY_RETENTION: 'N-day retention',
}

export function CampaignsPage() {
  const session = useSession()
  const { tenantId } = session
  const canManage = session.hasRole('tenant_admin')

  const {
    items: data,
    loading,
    loadingMore,
    error,
    nextCursor,
    loadMore,
    reload,
  } = useCursorPagination(
    (cursor) => api.listCampaigns(tenantId, cursor, 25),
    [tenantId],
  )
  const [editing, setEditing] = useState<Campaign | 'new' | null>(null)
  const [busy, setBusy] = useState(false)

  async function save(input: CampaignInput) {
    setBusy(true)
    try {
      if (editing === 'new') await api.createCampaign(tenantId, input)
      else if (editing) await api.updateCampaign(tenantId, editing.campaignId, input)
      setEditing(null)
      reload()
    } finally {
      setBusy(false)
    }
  }

  async function setStatus(c: Campaign, status: CampaignStatus) {
    setBusy(true)
    try {
      await api.updateCampaign(tenantId, c.campaignId, { status })
      reload()
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-xl font-semibold tracking-tight">Campaigns</h1>
          <p className="text-sm text-muted">
            Referral campaigns for this tenant. Codes are issued against an active campaign.
          </p>
        </div>
        {canManage && (
          <Button onClick={() => setEditing('new')} disabled={busy}>
            <Plus className="h-4 w-4" strokeWidth={1.75} /> New campaign
          </Button>
        )}
      </div>

      {editing && canManage && (
        <Panel title={editing === 'new' ? 'New campaign' : `Edit · ${editing.name}`}>
          <CampaignForm
            initial={editing === 'new' ? undefined : editing}
            busy={busy}
            onCancel={() => setEditing(null)}
            onSubmit={save}
          />
        </Panel>
      )}

      <Panel bodyClassName="p-0">
        {loading ? (
          <LoadingRows />
        ) : error && data.length === 0 ? (
          <ErrorNote message={error} />
        ) : data.length === 0 ? (
          <EmptyState
            icon={Megaphone}
            title="No campaigns yet"
            description={
              canManage
                ? 'Create a campaign so referral codes can be issued against it.'
                : 'This tenant has no campaigns configured.'
            }
          />
        ) : (
          <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase tracking-wider text-faint">
                <th className="px-4 py-2.5 font-medium">Campaign</th>
                <th className="px-4 py-2.5 font-medium">Status</th>
                <th className="px-4 py-2.5 font-medium">Bonus</th>
                <th className="px-4 py-2.5 font-medium">Conversion</th>
                <th className="px-4 py-2.5 font-medium">Cap</th>
                {canManage && <th className="px-4 py-2.5 font-medium">Actions</th>}
              </tr>
            </thead>
            <tbody>
              {data.map((c) => (
                <tr key={c.campaignId} className="border-b border-border/60 last:border-0">
                  <td className="px-4 py-3">
                    <div className="text-text">{c.name}</div>
                    <div className="font-mono text-[11px] text-faint">{c.campaignId}</div>
                  </td>
                  <td className="px-4 py-3"><StatusPill status={c.status} /></td>
                  <td className="px-4 py-3 font-mono text-text">{formatKes(c.bonusAmount)}</td>
                  <td className="px-4 py-3 text-muted">{CRITERIA_LABEL[c.conversionCriteria]}</td>
                  <td className="px-4 py-3 font-mono text-muted">{c.referralCapPerUser ?? '—'}</td>
                  {canManage && (
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-2">
                        <Button size="sm" variant="ghost" onClick={() => setEditing(c)} disabled={busy}>
                          <Pencil className="h-3.5 w-3.5" strokeWidth={1.75} /> Edit
                        </Button>
                        {c.status === 'ACTIVE' && (
                          <Button size="sm" variant="warn" onClick={() => setStatus(c, 'PAUSED')} disabled={busy}>
                            Pause
                          </Button>
                        )}
                        {c.status === 'PAUSED' && (
                          <Button size="sm" variant="success" onClick={() => setStatus(c, 'ACTIVE')} disabled={busy}>
                            Resume
                          </Button>
                        )}
                        {c.status !== 'ENDED' && (
                          <Button size="sm" variant="danger" onClick={() => setStatus(c, 'ENDED')} disabled={busy}>
                            End
                          </Button>
                        )}
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
          {error && <ErrorNote message={error} />}
          {nextCursor && (
            <div className="flex justify-center border-t border-border p-3">
              <Button onClick={loadMore} disabled={loadingMore}>
                {loadingMore ? 'Loading more…' : 'Load more'}
              </Button>
            </div>
          )}
          </div>
        )}
      </Panel>
    </div>
  )
}
