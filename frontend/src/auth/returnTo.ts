/**
 * Preserves the originally-requested route across the Keycloak redirect round-trip.
 *
 * The OIDC `redirect_uri` is fixed at the origin root, so after login the browser
 * lands on `/` and the deep link the user actually asked for (e.g. `/cases/42`)
 * would be lost. We stash it in sessionStorage before redirecting and restore it
 * once the app mounts post-login — robust against router/OIDC callback timing,
 * unlike relying on history state alone.
 */
const KEY = 'vigilant.returnTo'

export const returnTo = {
  /** Remember the current deep link before we bounce to Keycloak. */
  capture() {
    const path = window.location.pathname + window.location.search
    // Skip the root and the OIDC callback URL (`/?code=…&state=…`) — nothing to restore.
    if (path !== '/' && !path.startsWith('/?')) {
      sessionStorage.setItem(KEY, path)
    }
  },

  /** Consume the stashed route (one-shot). */
  take(): string | null {
    const value = sessionStorage.getItem(KEY)
    if (value) sessionStorage.removeItem(KEY)
    return value
  },
}
