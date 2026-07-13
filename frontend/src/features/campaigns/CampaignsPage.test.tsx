import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SessionProvider, makeSession } from '../../auth/session'
import { TenantProvider } from '../../state/tenant'
import { CampaignsPage } from './CampaignsPage'

// The page reads campaigns through the api swap point; stub it to an empty list
// so the role-gated controls (not the data) are what we assert on.
vi.mock('../../api', () => ({
  api: { listCampaigns: vi.fn().mockResolvedValue([]) },
}))

function renderAs(roles: string[]) {
  const session = makeSession({
    authenticated: true,
    username: 'x',
    tenantId: 'loob-bank',
    roles,
    logout: () => {},
  })
  render(
    <MemoryRouter>
      <SessionProvider value={session}>
        <TenantProvider tenants={[{ id: 'loob-bank', name: 'Loob Bank' }]} initialTenantId="loob-bank">
          <CampaignsPage />
        </TenantProvider>
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
})
