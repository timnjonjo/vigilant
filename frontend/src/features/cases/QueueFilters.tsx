import { Select } from '../../components/Select'
import { REASON_LABELS } from '../../lib/labels'
import type { Campaign, CaseStatus, ReasonCode } from '../../types/api'

export interface QueueFilterState {
  status: '' | CaseStatus
  reasonCode: '' | ReasonCode
  campaignId: string
  sortBy: 'score' | 'age'
}

const REASONS = Object.keys(REASON_LABELS) as ReasonCode[]

export function QueueFilters({
  value,
  campaigns,
  onChange,
}: {
  value: QueueFilterState
  campaigns: Campaign[]
  onChange: (next: QueueFilterState) => void
}) {
  const campaignOptions: [string, string][] = [
    ['', 'All campaigns'],
    ...campaigns.map((c) => [c.campaignId, c.name] as [string, string]),
  ]

  return (
    <div className="flex flex-wrap items-center gap-3">
      <Select
        label="Campaign"
        value={value.campaignId}
        onChange={(campaignId) => onChange({ ...value, campaignId })}
        options={campaignOptions}
      />
      <Select
        label="Status"
        value={value.status}
        onChange={(status) => onChange({ ...value, status })}
        options={[
          ['', 'All'],
          ['OPEN', 'Open'],
          ['RESOLVED', 'Resolved'],
        ]}
      />
      <Select
        label="Reason"
        value={value.reasonCode}
        onChange={(reasonCode) => onChange({ ...value, reasonCode })}
        options={[['', 'Any reason'], ...REASONS.map((c) => [c, REASON_LABELS[c]] as [ReasonCode, string])]}
      />
      <Select
        label="Sort"
        value={value.sortBy}
        onChange={(sortBy) => onChange({ ...value, sortBy })}
        options={[
          ['score', 'Score'],
          ['age', 'Age'],
        ]}
      />
    </div>
  )
}
