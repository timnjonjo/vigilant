import { ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { EmptyState } from '../../components/EmptyState'
import { ErrorNote } from '../../components/ErrorNote'
import { LoadingRows } from '../../components/LoadingState'
import { Panel } from '../../components/Panel'
import { api } from '../../api'
import { collectCursorPages } from '../../api/pagination'
import { useSession } from '../../auth/session'
import { Pagination } from '../../components/Pagination'
import { useAsync } from '../../lib/useAsync'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { usePagedCursor } from '../../lib/usePagedCursor'
import { CaseQueueTable } from './CaseQueueTable'
import { QueueFilters, type QueueFilterState } from './QueueFilters'

export function CaseQueuePage() {
  const { tenantId } = useSession()
  const [filters, setFilters] = useState<QueueFilterState>({
    status: 'OPEN',
    reasonCode: '',
    campaignId: '',
    search: '',
    sortBy: 'score',
  })
  // Keep the input snappy, but only query (and reset paging) once typing settles.
  const search = useDebouncedValue(filters.search.trim(), 300)

  const { data: campaigns } = useAsync(
    () => collectCursorPages((cursor) => api.listCampaigns(tenantId, cursor, 100)),
    [tenantId],
  )

  const cases = usePagedCursor(
    (cursor) =>
      api.listCases({
        tenantId,
        status: filters.status || undefined,
        reasonCode: filters.reasonCode || undefined,
        campaignId: filters.campaignId || undefined,
        search: search || undefined,
        sortBy: filters.sortBy,
        cursor,
        limit: 25,
      }),
    [tenantId, filters.status, filters.reasonCode, filters.campaignId, search, filters.sortBy],
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
        {cases.loading && cases.items.length === 0 ? (
          <LoadingRows />
        ) : cases.error && cases.items.length === 0 ? (
          <ErrorNote message={cases.error} />
        ) : cases.items.length === 0 ? (
          <EmptyState
            icon={ShieldCheck}
            title="No cases need review"
            description="Nothing matches this view. Adjust the filters, or check back as new events are scored."
          />
        ) : (
          <div>
            <CaseQueueTable cases={cases.items} />
            {cases.error && <ErrorNote message={cases.error} />}
            <Pagination
              page={cases.page}
              knownPages={cases.knownPages}
              hasNext={cases.hasNext}
              hasPrev={cases.hasPrev}
              loading={cases.loading}
              onGoTo={cases.goTo}
              onPrev={cases.prev}
              onNext={cases.next}
            />
          </div>
        )}
      </Panel>
    </div>
  )
}
