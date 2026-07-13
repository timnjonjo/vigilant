/**
 * Runtime configuration read from Vite env vars. The dashboard authenticates with
 * a Keycloak token (Bearer) attached in `src/api/http.ts` — no API key.
 *
 * VITE_USE_MOCK toggles the in-memory mock vs. the real backend transport — the
 * single switch behind `src/api/index.ts`.
 */
export const config = {
  useMock: (import.meta.env.VITE_USE_MOCK ?? 'true') !== 'false',
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
  defaultTenantId: import.meta.env.VITE_TENANT_ID ?? 'loob-bank',
}
