import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from './api'
import { AuthSplash } from './auth/AuthScreen'
import { returnTo } from './auth/returnTo'
import { useSession } from './auth/session'
import { AppShell } from './components/AppShell'
import { useAsync } from './lib/useAsync'
import { TenantProvider } from './state/tenant'

/** Loads the tenant list, then mounts the console scoped to the session's tenant. */
export function Root() {
  const session = useSession()
  const navigate = useNavigate()
  const { data: tenants } = useAsync(() => api.listTenants(), [])

  // After a login round-trip the browser lands on '/'; restore the route the
  // user originally requested (stashed before the Keycloak redirect). One-shot.
  useEffect(() => {
    const target = returnTo.take()
    if (target && target !== window.location.pathname + window.location.search) {
      navigate(target, { replace: true })
    }
  }, [navigate])

  if (!tenants) return <AuthSplash message="Loading your console…" />
  return (
    <TenantProvider tenants={tenants} initialTenantId={session.tenantId || undefined}>
      <AppShell />
    </TenantProvider>
  )
}
