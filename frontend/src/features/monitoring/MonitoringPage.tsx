import { useState } from 'react'
import { api } from '../../api'
import { useSession } from '../../auth/session'
import { ErrorNote } from '../../components/ErrorNote'
import { LoadingRows } from '../../components/LoadingState'
import { Panel } from '../../components/Panel'
import { Select } from '../../components/Select'
import { StatCard } from '../../components/StatCard'
import { formatKes, formatPct } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'
import { AlertPanel } from './AlertPanel'
import { FraudRateChart } from './FraudRateChart'
import { TopCodesTable } from './TopCodesTable'

export function MonitoringPage() {
  const { tenantId } = useSession()
  const [campaignId, setCampaignId] = useState('')
  const { data: campaigns } = useAsync(() => api.listCampaigns(tenantId), [tenantId])
  const { data, loading, error } = useAsync(
    () => api.getMonitoring(tenantId, 30, campaignId || undefined),
    [tenantId, campaignId],
  )

  const campaignOptions: [string, string][] = [
    ['', 'All campaigns'],
    ...(campaigns ?? []).map((c) => [c.campaignId, c.name] as [string, string]),
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-xl font-semibold tracking-tight">Monitoring</h1>
          <p className="text-sm text-muted">Aggregate fraud signal across the tenant.</p>
        </div>
        <Select label="Campaign" value={campaignId} options={campaignOptions} onChange={setCampaignId} />
      </div>

      {loading ? (
        <LoadingRows />
      ) : error || !data ? (
        <ErrorNote message={error ?? 'No monitoring data'} />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <StatCard
              label="Blocked / held value"
              value={formatKes(data.blockedValueKes, true)}
              trendPct={data.blockedValueTrendPct}
              hint="trailing 30 days"
            />
            <StatCard
              label="False-positive rate"
              value={formatPct(data.falsePositiveRatePct)}
              trendPct={data.falsePositiveTrendPct}
              hint="of reviewed cases"
            />
            <StatCard label="Open cases" value={data.openCaseCount} hint="awaiting review" />
          </div>

          <Panel title="Flagged rate over time">
            <FraudRateChart data={data.fraudRateSeries} />
          </Panel>

          <div className="grid gap-4 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
            <Panel title="Top referral codes" bodyClassName="p-0">
              <TopCodesTable codes={data.topCodes} />
            </Panel>
            <AlertPanel alerts={data.alerts} />
          </div>
        </>
      )}
    </div>
  )
}
