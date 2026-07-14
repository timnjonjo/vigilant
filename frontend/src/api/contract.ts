import type {
  AuditEntry,
  Campaign,
  CampaignInput,
  CaseAction,
  CaseGraph,
  CaseQuery,
  CaseView,
  MonitoringSummary,
  Tenant,
  CursorPage,
} from '../types/api'

/**
 * The one interface both the mock and the real backend implement. Swapping mock
 * → real is a single-line change in `index.ts`; every screen depends only on this.
 */
export interface VigilantApi {
  listTenants(): Promise<Tenant[]>
  listCases(query: CaseQuery): Promise<CursorPage<CaseView>>
  getCase(tenantId: string, id: number): Promise<CaseView>
  /** Forward contract (spec §8) — no backend endpoint yet. */
  getCaseGraph(tenantId: string, id: number): Promise<CaseGraph>
  /** Forward contract (spec §8) — no backend endpoint yet. */
  getCaseAudit(
    tenantId: string,
    id: number,
    cursor?: string,
    limit?: number,
  ): Promise<CursorPage<AuditEntry>>
  actOnCase(
    tenantId: string,
    id: number,
    action: CaseAction,
    actor: string,
    note?: string,
  ): Promise<CaseView>
  getMonitoring(tenantId: string, rangeDays: number, campaignId?: string): Promise<MonitoringSummary>

  // Campaigns (spec §10a). Reads are open to any role; writes are tenant_admin
  // only (the backend enforces this — the UI just hides the controls).
  listCampaigns(tenantId: string, cursor?: string, limit?: number): Promise<CursorPage<Campaign>>
  createCampaign(tenantId: string, input: CampaignInput): Promise<Campaign>
  updateCampaign(
    tenantId: string,
    campaignId: string,
    input: Partial<CampaignInput>,
  ): Promise<Campaign>
}
