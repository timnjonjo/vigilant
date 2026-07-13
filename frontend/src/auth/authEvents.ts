/**
 * Bridges the plain-fetch api layer to the React auth layer for the one thing it
 * can't do itself: on a 401 (token rejected mid-session), the session provider
 * registers a handler here that redirects to Keycloak login.
 */
type Handler = () => void

let unauthorizedHandler: Handler = () => {}

export const authEvents = {
  onUnauthorized(handler: Handler): void {
    unauthorizedHandler = handler
  },
  emitUnauthorized(): void {
    unauthorizedHandler()
  },
}
