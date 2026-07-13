import type { VigilantApi } from '../contract'
import type { Campaign, CaseView } from '../../types/api'
import { http } from '../http'

/**
 * Real backend transport. Endpoints: `/v1/cases[...]`, `/v1/monitoring`,
 * `/v1/tenants`, and `/v1/campaigns[...]` (spec §10a). Reason-code filter + sort
 * are client-side (the backend filters by status + campaign). Auth: the Keycloak
 * Bearer token is attached by `http`; 401/403 surface as thrown errors.
 */
export const httpApi: VigilantApi = {
  listTenants: () => http('/v1/tenants'),

  listCases: async ({ tenantId, status, reasonCode, campaignId, sortBy }) => {
    const params = new URLSearchParams({ tenantId })
    if (status) params.set('status', status)
    if (campaignId) params.set('campaignId', campaignId)
    let rows = await http<CaseView[]>(`/v1/cases?${params.toString()}`)
    if (reasonCode) rows = rows.filter((c) => c.reasonCodes.includes(reasonCode))
    return [...rows].sort((a, b) =>
      sortBy === 'age'
        ? new Date(a.openedAt).getTime() - new Date(b.openedAt).getTime()
        : b.score - a.score,
    )
  },

  getCase: (tenantId, id) =>
    http(`/v1/cases/${id}?tenantId=${encodeURIComponent(tenantId)}`),

  getCaseGraph: (tenantId, id) =>
    http(`/v1/cases/${id}/graph?tenantId=${encodeURIComponent(tenantId)}`),

  getCaseAudit: (tenantId, id) =>
    http(`/v1/cases/${id}/audit?tenantId=${encodeURIComponent(tenantId)}`),

  actOnCase: (tenantId, id, action, actor) => {
    // The backend's decision endpoint is final-only (APPROVE/REJECT). Hold and
    // Escalate are non-final dashboard actions with no backend path yet.
    if (action === 'HOLD' || action === 'ESCALATE') {
      return Promise.reject(new Error(`${action} isn’t supported by the backend yet (resolve is final-only)`))
    }
    return http(`/v1/cases/${id}/resolve`, {
      method: 'POST',
      body: JSON.stringify({ tenantId, resolution: action, resolvedBy: actor }),
    })
  },

  getMonitoring: (tenantId, _rangeDays, campaignId) => {
    const params = new URLSearchParams({ tenantId })
    if (campaignId) params.set('campaignId', campaignId)
    return http(`/v1/monitoring?${params.toString()}`)
  },

  listCampaigns: (tenantId) =>
    http(`/v1/campaigns?tenantId=${encodeURIComponent(tenantId)}`),

  createCampaign: (tenantId, input) =>
    http<Campaign>('/v1/campaigns', {
      method: 'POST',
      body: JSON.stringify({ tenantId, ...input }),
    }),

  updateCampaign: (tenantId, campaignId, input) =>
    http<Campaign>(`/v1/campaigns/${encodeURIComponent(campaignId)}`, {
      method: 'PATCH',
      body: JSON.stringify({ tenantId, ...input }),
    }),
}
