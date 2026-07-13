import { createContext, useContext, useEffect, useRef, type ReactNode } from 'react'
import { useAuth } from 'react-oidc-context'
import { config } from '../config'
import { AuthError, AuthSplash } from './AuthScreen'
import { authEvents } from './authEvents'
import { decodeJwt } from './jwt'
import { returnTo } from './returnTo'
import { tokenStore } from './tokenStore'

export type Role = 'fraud_analyst' | 'ops_viewer' | 'tenant_admin'

export interface Session {
  authenticated: boolean
  username: string
  tenantId: string
  roles: string[]
  hasRole: (role: Role) => boolean
  logout: () => void
}

const SessionContext = createContext<Session | null>(null)

export function useSession(): Session {
  const ctx = useContext(SessionContext)
  if (!ctx) throw new Error('useSession must be used within a session provider')
  return ctx
}

/** Builds a Session from its data (adds the hasRole helper). Exported for tests. */
export function makeSession(base: Omit<Session, 'hasRole'>): Session {
  return { ...base, hasRole: (role) => base.roles.includes(role) }
}

/** Provides an explicit Session value. Used by the real/mock providers and tests. */
export function SessionProvider({ value, children }: { value: Session; children: ReactNode }) {
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

interface AccessClaims {
  tenant_id?: string
  realm_access?: { roles?: string[] }
  preferred_username?: string
}

/** Real Keycloak session: gates on login, derives tenant + roles from the token. */
export function RealSessionProvider({ children }: { children: ReactNode }) {
  const auth = useAuth()

  // Keep the plain-fetch layer's bearer token current. Set SYNCHRONOUSLY during
  // render — NOT in an effect: React runs child effects before parent ones, so an
  // effect here would let a child's first API call (e.g. Root's listTenants) fire
  // before the token is stored. That request 401s → triggers re-login → the live
  // Keycloak session bounces straight back → loops. Setting it top-down in render
  // guarantees the token is present before any child effect runs.
  tokenStore.set(auth.user?.access_token ?? null)

  const autoSigninTried = useRef(false)
  const lastReloginAt = useRef(0)

  // A 401 (token rejected mid-session) → back to Keycloak. Debounced so a
  // persistently-rejected token can't spin the redirect in a tight loop.
  useEffect(() => {
    authEvents.onUnauthorized(() => {
      const now = Date.now()
      if (now - lastReloginAt.current < 10_000) return
      lastReloginAt.current = now
      returnTo.capture()
      void auth.signinRedirect()
    })
  }, [auth])

  // Auto-start login for an unauthenticated visitor — once, and never while a
  // redirect/callback is already in flight (guards against redirect loops and
  // StrictMode double-invocation orphaning the PKCE state).
  useEffect(() => {
    if (
      !auth.isLoading &&
      !auth.activeNavigator &&
      !auth.isAuthenticated &&
      !auth.error &&
      !autoSigninTried.current
    ) {
      autoSigninTried.current = true
      returnTo.capture()
      void auth.signinRedirect()
    }
  }, [auth])

  if (auth.isLoading || auth.activeNavigator) return <AuthSplash message="Signing you in…" />
  if (auth.error) {
    return (
      <AuthError
        message={auth.error.message}
        onRetry={() => {
          autoSigninTried.current = false
          void auth.signinRedirect()
        }}
      />
    )
  }
  if (!auth.isAuthenticated || !auth.user) {
    return <AuthSplash message="Redirecting to secure sign-in…" />
  }

  const claims = decodeJwt<AccessClaims>(auth.user.access_token) ?? {}
  const session = makeSession({
    authenticated: true,
    username:
      (auth.user.profile.preferred_username as string | undefined) ??
      claims.preferred_username ??
      'analyst',
    tenantId: claims.tenant_id ?? '',
    roles: claims.realm_access?.roles ?? [],
    logout: () => void auth.signoutRedirect(),
  })

  return <SessionProvider value={session}>{children}</SessionProvider>
}

/** Mock session: bypasses Keycloak entirely (full access, no login) for UI dev. */
export function MockSessionProvider({ children }: { children: ReactNode }) {
  const session = makeSession({
    authenticated: true,
    username: 'mock-analyst',
    tenantId: config.defaultTenantId,
    roles: ['fraud_analyst', 'ops_viewer', 'tenant_admin'],
    logout: () => {},
  })
  return <SessionProvider value={session}>{children}</SessionProvider>
}
