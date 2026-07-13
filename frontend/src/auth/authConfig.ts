import type { AuthProviderProps } from 'react-oidc-context'

/**
 * OIDC settings for the Keycloak `vigilant-dashboard` public client (PKCE).
 * Redirect-based login — Keycloak owns credentials. Silent renew uses the
 * refresh token (no hidden iframe).
 */
export const oidcConfig: AuthProviderProps = {
  authority:
    import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8081/realms/vigilant',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'vigilant-dashboard',
  redirect_uri: window.location.origin + '/',
  post_logout_redirect_uri: window.location.origin + '/',
  response_type: 'code',
  scope: 'openid profile',
  automaticSilentRenew: true,
  // Strip the ?code=…&state=… off the URL after the login round-trip.
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}
