// Mirrors the backend REST contracts (spec §7). Prefer generating from the live
// OpenAPI spec (/v3/api-docs) once the backend grows the dashboard endpoints —
// see README. Types marked "forward contract" have no backend endpoint yet and
// are defined here by the mock (spec §8).

export type Decision = 'APPROVE' | 'HOLD' | 'REJECT'
export type CaseStatus = 'OPEN' | 'RESOLVED'
export type ReasonCode =
  | 'VELOCITY_BURST'
  | 'DEVICE_COLLISION'
  | 'IP_SUBNET_COLLISION'
  | 'CYCLE_DETECTED'
  | 'DATACENTER_OR_VPN_IP'

/** Analyst action on a case. ESCALATE is dashboard-only (not a backend Decision). */
export type CaseAction = 'APPROVE' | 'HOLD' | 'REJECT' | 'ESCALATE'

/** Read model returned by the case-queue endpoints (backend `CaseView`). */
export interface CaseView {
  id: number
  tenantId: string
  campaignId: string
  referralCode: string
  refereeUserId: string
  decision: Decision
  score: number
  reasonCodes: ReasonCode[]
  status: CaseStatus
  resolution: Decision | null
  resolvedBy: string | null
  openedAt: string
  resolvedAt: string | null
}

// ── Campaigns (spec §10a) ──────────────────────────────────────────────────

export type CampaignStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'ENDED'
export type ConversionCriteria = 'FIRST_DEPOSIT' | 'N_DAY_RETENTION'

/** A tenant campaign (backend `CampaignView`). */
export interface Campaign {
  campaignId: string
  tenantId: string
  name: string
  bonusAmount: number
  startDate: string | null
  endDate: string | null
  status: CampaignStatus
  conversionCriteria: ConversionCriteria
  referralCapPerUser: number | null
  createdAt: string
  updatedAt: string
}

/** Create/edit payload — tenantId is added by the API layer. */
export interface CampaignInput {
  name: string
  bonusAmount: number
  startDate?: string | null
  endDate?: string | null
  status: CampaignStatus
  conversionCriteria: ConversionCriteria
  referralCapPerUser?: number | null
}

// ── Forward contracts (spec §8, not yet in the backend) ────────────────────

export type NodeRole = 'referrer' | 'referee'
export type IpType = 'RESIDENTIAL' | 'MOBILE' | 'DATACENTER' | 'UNKNOWN'
export type EdgeType = 'REFERRED' | 'SHARES_DEVICE' | 'SHARES_IP_SUBNET'

export interface GraphNode {
  id: string
  userId: string
  role: NodeRole
  ipType: IpType
  converted: boolean
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  type: EdgeType
}

/** The flagged subgraph for a case's referral cluster. */
export interface CaseGraph {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export interface AuditEntry {
  id: string
  at: string
  actor: string
  action: CaseAction | 'OPENED'
  note: string
}

// Ops / monitoring aggregates.
export interface FraudRatePoint {
  date: string
  flaggedRate: number
  reviewedRate: number
}

export interface TopCode {
  referralCode: string
  tenantId: string
  volume: number
  riskScore: number
  blockedKes: number
}

export type AlertSeverityLevel = 'critical' | 'high' | 'medium' | 'low'

export interface OpsAlert {
  id: string
  severity: AlertSeverityLevel
  title: string
  detail: string
  at: string
}

export interface MonitoringSummary {
  blockedValueKes: number
  blockedValueTrendPct: number
  falsePositiveRatePct: number
  falsePositiveTrendPct: number
  openCaseCount: number
  fraudRateSeries: FraudRatePoint[]
  topCodes: TopCode[]
  alerts: OpsAlert[]
}

// ── Query/command shapes ───────────────────────────────────────────────────

export interface CaseQuery {
  tenantId: string
  status?: CaseStatus
  reasonCode?: ReasonCode
  campaignId?: string
  sortBy?: 'score' | 'age'
}

export interface Tenant {
  id: string
  name: string
}
