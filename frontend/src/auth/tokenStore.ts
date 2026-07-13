/**
 * Holds the current access token outside React so the plain-fetch api layer can
 * attach it without prop-drilling. Kept in sync by the session provider.
 */
let accessToken: string | null = null

export const tokenStore = {
  get: (): string | null => accessToken,
  set: (token: string | null): void => {
    accessToken = token
  },
}
