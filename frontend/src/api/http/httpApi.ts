import type { VigilantApi } from '../contract'
import type { AuditEntry, Campaign, CaseView, CursorPage } from '../../types/api'
import { http } from '../http'

/**
 * Real backend transport. Endpoints: `/v1/cases[...]`, `/v1/monitoring`,
 * `/v1/tenants`, and `/v1/campaigns[...]` (spec §10a). Reason-code filter + sort
 * are client-side (the backend filters by status + campaign). Auth: the Keycloak
 * Bearer token is attached by `http`; 401/403 surface as thrown errors.
 */
export const httpApi: VigilantApi = {
  listTenants: () => http('/v1/tenants'),

  listCases: ({ tenantId, status, reasonCode, campaignId, search, sortBy, cursor, limit }) => {
    const params = new URLSearchParams({ tenantId })
    if (status) params.set('status', status)
    if (campaignId) params.set('campaignId', campaignId)
    if (reasonCode) params.set('reasonCode', reasonCode)
    if (search) params.set('search', search)
    if (sortBy) params.set('sortBy', sortBy)
    if (cursor) params.set('cursor', cursor)
    if (limit) params.set('limit', String(limit))
    return http<CursorPage<CaseView>>(`/v1/cases?${params.toString()}`)
  },

  getCase: (tenantId, id) =>
    http(`/v1/cases/${id}?tenantId=${encodeURIComponent(tenantId)}`),

  getCaseGraph: (tenantId, id) =>
    http(`/v1/cases/${id}/graph?tenantId=${encodeURIComponent(tenantId)}`),

  getCaseAudit: (tenantId, id, cursor, limit) => {
    const params = new URLSearchParams({ tenantId })
    if (cursor) params.set('cursor', cursor)
    if (limit) params.set('limit', String(limit))
    return http<CursorPage<AuditEntry>>(`/v1/cases/${id}/audit?${params.toString()}`)
  },

  actOnCase: (tenantId, id, action, _actor) => {
    // The backend's decision endpoint is final-only (APPROVE/REJECT). Hold and
    // Escalate are non-final dashboard actions with no backend path yet.
    if (action === 'HOLD' || action === 'ESCALATE') {
      return Promise.reject(new Error(`${action} isn’t supported by the backend yet (resolve is final-only)`))
    }
    return http(`/v1/cases/${id}/resolve`, {
      method: 'POST',
      body: JSON.stringify({ tenantId, resolution: action }),
    })
  },

  getMonitoring: (tenantId, _rangeDays, campaignId) => {
    const params = new URLSearchParams({ tenantId })
    if (campaignId) params.set('campaignId', campaignId)
    return http(`/v1/monitoring?${params.toString()}`)
  },

  listCampaigns: (tenantId, cursor, limit) => {
    const params = new URLSearchParams({ tenantId })
    if (cursor) params.set('cursor', cursor)
    if (limit) params.set('limit', String(limit))
    return http<CursorPage<Campaign>>(`/v1/campaigns?${params.toString()}`)
  },

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
