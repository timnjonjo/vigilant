/** Decodes a JWT payload (no verification — the backend verifies; this is for UI). */
export function decodeJwt<T = Record<string, unknown>>(token: string): T | null {
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(json) as T
  } catch {
    return null
  }
}
