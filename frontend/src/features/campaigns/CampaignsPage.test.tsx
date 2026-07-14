import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SessionProvider, makeSession } from '../../auth/session'
import { api } from '../../api'
import { CampaignsPage } from './CampaignsPage'

// The page reads campaigns through the api swap point; stub it to an empty page
// so the role-gated controls (not the data) are what we assert on.
vi.mock('../../api', () => ({
  api: { listCampaigns: vi.fn().mockResolvedValue({ items: [], nextCursor: null }) },
}))

// The tenant comes from the session (the token's tenant_id claim) — there is no
// tenant-selection step, so a session is the only context the page needs.
function renderAs(roles: string[], tenantId = 'loob-bank') {
  const session = makeSession({
    authenticated: true,
    username: 'x',
    tenantId,
    roles,
    logout: () => {},
  })
  render(
    <MemoryRouter>
      <SessionProvider value={session}>
        <CampaignsPage />
      </SessionProvider>
    </MemoryRouter>,
  )
}

describe('CampaignsPage role gating', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows management controls to a tenant admin', async () => {
    renderAs(['tenant_admin'])
    expect(await screen.findByRole('button', { name: /new campaign/i })).toBeInTheDocument()
  })

  it('hides the create control from a fraud analyst', async () => {
    renderAs(['fraud_analyst'])
    // Wait for the page to settle, then assert the control is absent.
    await screen.findByRole('heading', { name: 'Campaigns' })
    expect(screen.queryByRole('button', { name: /new campaign/i })).not.toBeInTheDocument()
  })

  it("scopes API calls to the token's tenant with no selection step", async () => {
    renderAs(['tenant_admin'], 'acme-sacco')
    await screen.findByRole('heading', { name: 'Campaigns' })
    // The tenant argument is the session's tenant_id, taken straight from the
    // token — never a value the user picked from a switcher.
    expect(api.listCampaigns).toHaveBeenCalledWith('acme-sacco', undefined, 25)
  })
})
