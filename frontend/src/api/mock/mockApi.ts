import type { VigilantApi } from '../contract'
import type { AuditEntry, Campaign, CaseAction, CaseView } from '../../types/api'
import {
  TENANTS,
  seedCases,
  seedCampaigns,
  CASE_GRAPHS,
  SEED_AUDIT,
  buildMonitoring,
} from './fixtures'

const delay = <T>(value: T, ms = 260): Promise<T> =>
  new Promise((resolve) => setTimeout(() => resolve(structuredClone(value)), ms))

// Mutable in-memory state for the session; resolving a case / editing a campaign
// updates it here.
const cases: CaseView[] = seedCases()
const audit: Record<number, AuditEntry[]> = structuredClone(SEED_AUDIT)
const campaigns: Campaign[] = seedCampaigns()
let campaignSeq = 900

function locate(tenantId: string, id: number): CaseView {
  const found = cases.find((c) => c.id === id && c.tenantId === tenantId)
  if (!found) throw new Error(`Case ${id} not found`)
  return found
}

const NOTE: Record<CaseAction, string> = {
  APPROVE: 'Approved payout — cleared as genuine.',
  REJECT: 'Rejected payout — confirmed fraudulent.',
  HOLD: 'Kept on hold pending further evidence.',
  ESCALATE: 'Escalated for senior review.',
}

export const mockApi: VigilantApi = {
  listTenants: () => delay(TENANTS),

  listCases: (query) => {
    let rows = cases.filter((c) => c.tenantId === query.tenantId)
    if (query.status) rows = rows.filter((c) => c.status === query.status)
    if (query.reasonCode) rows = rows.filter((c) => c.reasonCodes.includes(query.reasonCode!))
    if (query.campaignId) rows = rows.filter((c) => c.campaignId === query.campaignId)
    rows = [...rows].sort((a, b) =>
      query.sortBy === 'age'
        ? new Date(a.openedAt).getTime() - new Date(b.openedAt).getTime()
        : b.score - a.score,
    )
    return delay(rows)
  },

  getCase: (tenantId, id) => delay(locate(tenantId, id)),

  getCaseGraph: (_tenantId, id) => delay(CASE_GRAPHS[id] ?? { nodes: [], edges: [] }),

  getCaseAudit: (_tenantId, id) => delay(audit[id] ?? []),

  actOnCase: (tenantId, id, action, actor, note) => {
    const target = locate(tenantId, id)
    const at = new Date().toISOString()
    if (action === 'APPROVE' || action === 'REJECT') {
      target.status = 'RESOLVED'
      target.resolution = action
      target.resolvedBy = actor
      target.resolvedAt = at
    }
    // HOLD / ESCALATE leave the case open in the queue.
    ;(audit[id] ??= []).push({
      id: `a${id}-${audit[id]?.length ?? 0}`,
      at,
      actor,
      action,
      note: note?.trim() || NOTE[action],
    })
    return delay(target)
  },

  getMonitoring: (tenantId) => delay(buildMonitoring(tenantId)),

  listCampaigns: (tenantId) => delay(campaigns.filter((c) => c.tenantId === tenantId)),

  createCampaign: (tenantId, input) => {
    const now = new Date().toISOString()
    const campaign: Campaign = {
      campaignId: `camp-${++campaignSeq}`,
      tenantId,
      name: input.name,
      bonusAmount: input.bonusAmount,
      startDate: input.startDate ?? null,
      endDate: input.endDate ?? null,
      status: input.status,
      conversionCriteria: input.conversionCriteria,
      referralCapPerUser: input.referralCapPerUser ?? null,
      createdAt: now,
      updatedAt: now,
    }
    campaigns.unshift(campaign)
    return delay(campaign)
  },

  updateCampaign: (tenantId, campaignId, input) => {
    const target = campaigns.find((c) => c.campaignId === campaignId && c.tenantId === tenantId)
    if (!target) throw new Error(`Campaign ${campaignId} not found`)
    Object.assign(target, input, { updatedAt: new Date().toISOString() })
    return delay(target)
  },
}
