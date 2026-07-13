import { useState, type ReactNode } from 'react'
import { Button } from '../../components/Button'
import { Select } from '../../components/Select'
import type {
  Campaign,
  CampaignInput,
  CampaignStatus,
  ConversionCriteria,
} from '../../types/api'

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs uppercase tracking-wider text-faint">{label}</span>
      {children}
    </label>
  )
}

const inputClass =
  'rounded-md border border-border bg-surface px-2.5 py-1.5 text-sm text-text outline-none ' +
  'hover:bg-surface-2 focus-visible:ring-2 focus-visible:ring-border-strong'

const STATUSES: [CampaignStatus, string][] = [
  ['DRAFT', 'Draft'],
  ['ACTIVE', 'Active'],
  ['PAUSED', 'Paused'],
  ['ENDED', 'Ended'],
]
const CRITERIA: [ConversionCriteria, string][] = [
  ['FIRST_DEPOSIT', 'First deposit'],
  ['N_DAY_RETENTION', 'N-day retention'],
]

/** Create/edit form for a campaign. Controlled, minimal validation (name + bonus). */
export function CampaignForm({
  initial,
  busy,
  onSubmit,
  onCancel,
}: {
  initial?: Campaign
  busy: boolean
  onSubmit: (input: CampaignInput) => void
  onCancel: () => void
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [bonusAmount, setBonusAmount] = useState(String(initial?.bonusAmount ?? '350'))
  const [status, setStatus] = useState<CampaignStatus>(initial?.status ?? 'DRAFT')
  const [criteria, setCriteria] = useState<ConversionCriteria>(
    initial?.conversionCriteria ?? 'FIRST_DEPOSIT',
  )
  const [cap, setCap] = useState(initial?.referralCapPerUser?.toString() ?? '')
  const [startDate, setStartDate] = useState(initial?.startDate ?? '')
  const [endDate, setEndDate] = useState(initial?.endDate ?? '')

  const bonusValue = Number(bonusAmount)
  const valid = name.trim().length > 0 && Number.isFinite(bonusValue) && bonusValue >= 0

  function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!valid) return
    onSubmit({
      name: name.trim(),
      bonusAmount: bonusValue,
      status,
      conversionCriteria: criteria,
      referralCapPerUser: cap.trim() === '' ? null : Number(cap),
      startDate: startDate || null,
      endDate: endDate || null,
    })
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Name">
          <input className={inputClass} value={name} onChange={(e) => setName(e.target.value)}
            placeholder="Q3 Signup Boost" aria-label="Name" />
        </Field>
        <Field label="Bonus amount (KES)">
          <input className={inputClass} type="number" min="0" step="1" value={bonusAmount}
            onChange={(e) => setBonusAmount(e.target.value)} aria-label="Bonus amount" />
        </Field>
        <Field label="Status">
          <Select label="" value={status} options={STATUSES} onChange={setStatus} />
        </Field>
        <Field label="Conversion criteria">
          <Select label="" value={criteria} options={CRITERIA} onChange={setCriteria} />
        </Field>
        <Field label="Referral cap / user (optional)">
          <input className={inputClass} type="number" min="0" step="1" value={cap}
            onChange={(e) => setCap(e.target.value)} placeholder="none" aria-label="Referral cap" />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Start date">
            <input className={inputClass} type="date" value={startDate ?? ''}
              onChange={(e) => setStartDate(e.target.value)} aria-label="Start date" />
          </Field>
          <Field label="End date">
            <input className={inputClass} type="date" value={endDate ?? ''}
              onChange={(e) => setEndDate(e.target.value)} aria-label="End date" />
          </Field>
        </div>
      </div>

      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button type="submit" variant="success" disabled={!valid || busy}>
          {initial ? 'Save changes' : 'Create campaign'}
        </Button>
      </div>
    </form>
  )
}
