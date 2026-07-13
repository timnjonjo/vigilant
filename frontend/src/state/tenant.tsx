import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { config } from '../config'
import type { Tenant } from '../types/api'

/**
 * Tenant selection. Multi-tenancy is an open decision (spec §10), so this is a
 * shallow, cosmetic switch — remove the provider and the switcher and nothing
 * else needs rewiring.
 */
interface TenantState {
  tenantId: string
  setTenantId: (id: string) => void
  tenants: Tenant[]
}

const TenantContext = createContext<TenantState | null>(null)

export function TenantProvider({
  tenants,
  initialTenantId,
  children,
}: {
  tenants: Tenant[]
  initialTenantId?: string
  children: ReactNode
}) {
  const [tenantId, setTenantId] = useState(initialTenantId ?? config.defaultTenantId)
  const value = useMemo(() => ({ tenantId, setTenantId, tenants }), [tenantId, tenants])
  return <TenantContext.Provider value={value}>{children}</TenantContext.Provider>
}

export function useTenant(): TenantState {
  const ctx = useContext(TenantContext)
  if (!ctx) throw new Error('useTenant must be used within a TenantProvider')
  return ctx
}
