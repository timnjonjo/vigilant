import { authEvents } from '../auth/authEvents'
import { tokenStore } from '../auth/tokenStore'
import { config } from '../config'

/** A failed backend call, carrying the HTTP status so the UI can react by kind. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

function messageFor(status: number): string {
  if (status === 403) return 'Not authorized — your role or tenant doesn’t allow this.'
  if (status === 404) return 'Not found.'
  if (status >= 500) return 'The server had a problem. Please try again shortly.'
  return `Request failed (${status}).`
}

/**
 * Thin fetch wrapper for the real backend: attaches the Keycloak access token,
 * parses JSON, and maps failures to a typed {@link ApiError}. A 401 (session
 * expired/revoked) triggers a re-login rather than surfacing a broken page.
 */
export async function http<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = tokenStore.get()

  let response: Response
  try {
    response = await fetch(`${config.apiBaseUrl}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init.headers,
      },
    })
  } catch {
    throw new ApiError(0, 'Can’t reach the Vigilant backend.')
  }

  if (!response.ok) {
    if (response.status === 401) {
      authEvents.emitUnauthorized() // token rejected → back to Keycloak login
      throw new ApiError(401, 'Your session expired — signing you back in…')
    }
    throw new ApiError(response.status, messageFor(response.status))
  }

  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}
