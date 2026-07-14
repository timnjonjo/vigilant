import { Search, X } from 'lucide-react'
import { Select } from '../../components/Select'
import { REASON_LABELS } from '../../lib/labels'
import type { Campaign, CaseStatus, ReasonCode } from '../../types/api'

export interface QueueFilterState {
  status: '' | CaseStatus
  reasonCode: '' | ReasonCode
  campaignId: string
  search: string
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
      <label className="relative">
        <span className="sr-only">Search cases</span>
        <Search
          className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-faint"
          strokeWidth={1.75}
        />
        <input
          type="search"
          value={value.search}
          onChange={(e) => onChange({ ...value, search: e.target.value })}
          placeholder="Code or user id…"
          aria-label="Search by referral code or user id"
          className="h-9 w-52 rounded-md border border-border bg-surface-2 pl-8 pr-8 text-sm text-text placeholder:text-faint focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-border-strong"
        />
        {value.search && (
          <button
            type="button"
            onClick={() => onChange({ ...value, search: '' })}
            aria-label="Clear search"
            className="absolute right-2 top-1/2 -translate-y-1/2 text-faint hover:text-text"
          >
            <X className="h-4 w-4" strokeWidth={1.75} />
          </button>
        )}
      </label>
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
