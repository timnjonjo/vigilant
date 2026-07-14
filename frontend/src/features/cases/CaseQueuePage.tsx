import { ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { EmptyState } from '../../components/EmptyState'
import { ErrorNote } from '../../components/ErrorNote'
import { LoadingRows } from '../../components/LoadingState'
import { Panel } from '../../components/Panel'
import { api } from '../../api'
import { useSession } from '../../auth/session'
import { useAsync } from '../../lib/useAsync'
import { CaseQueueTable } from './CaseQueueTable'
import { QueueFilters, type QueueFilterState } from './QueueFilters'

export function CaseQueuePage() {
  const { tenantId } = useSession()
  const [filters, setFilters] = useState<QueueFilterState>({
    status: 'OPEN',
    reasonCode: '',
    campaignId: '',
    sortBy: 'score',
  })

  const { data: campaigns } = useAsync(() => api.listCampaigns(tenantId), [tenantId])

  const { data, loading, error } = useAsync(
    () =>
      api.listCases({
        tenantId,
        status: filters.status || undefined,
        reasonCode: filters.reasonCode || undefined,
        campaignId: filters.campaignId || undefined,
        sortBy: filters.sortBy,
      }),
    [tenantId, filters.status, filters.reasonCode, filters.campaignId, filters.sortBy],
  )

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-xl font-semibold tracking-tight">Case Queue</h1>
          <p className="text-sm text-muted">Flagged referral payouts awaiting review.</p>
        </div>
        <QueueFilters value={filters} campaigns={campaigns ?? []} onChange={setFilters} />
      </div>

      <Panel bodyClassName="p-0">
        {loading ? (
          <LoadingRows />
        ) : error ? (
          <ErrorNote message={error} />
        ) : !data || data.length === 0 ? (
          <EmptyState
            icon={ShieldCheck}
            title="No cases need review"
            description="Nothing matches this view. Adjust the filters, or check back as new events are scored."
          />
        ) : (
          <CaseQueueTable cases={data} />
        )}
      </Panel>
    </div>
  )
}
