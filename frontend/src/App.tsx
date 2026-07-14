import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { returnTo } from './auth/returnTo'
import { AppShell } from './components/AppShell'

/**
 * Mounts the console. Tenant context is the token's `tenant_id` claim (a user
 * belongs to exactly one tenant), read from the session where roles are — there
 * is no tenant-selection step.
 */
export function Root() {
  const navigate = useNavigate()

  // After a login round-trip the browser lands on '/'; restore the route the
  // user originally requested (stashed before the Keycloak redirect). One-shot.
  useEffect(() => {
    const target = returnTo.take()
    if (target && target !== window.location.pathname + window.location.search) {
      navigate(target, { replace: true })
    }
  }, [navigate])

  return <AppShell />
}
