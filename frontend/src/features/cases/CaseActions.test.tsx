import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SessionProvider, makeSession } from '../../auth/session'
import type { CaseView } from '../../types/api'
import { CaseActions } from './CaseActions'

const openCase: CaseView = {
  id: 1,
  tenantId: 'loob-bank',
  referralCode: 'LOOB-1',
  refereeUserId: 'u2',
  decision: 'HOLD',
  score: 0.5,
  reasonCodes: [],
  status: 'OPEN',
  resolution: null,
  resolvedBy: null,
  openedAt: '2026-07-10T10:00:00Z',
  resolvedAt: null,
}

function renderWithRoles(roles: string[]) {
  const session = makeSession({
    authenticated: true,
    username: 'x',
    tenantId: 'loob-bank',
    roles,
    logout: () => {},
  })
  render(
    <SessionProvider value={session}>
      <CaseActions c={openCase} onResolved={() => {}} />
    </SessionProvider>,
  )
}

describe('CaseActions role gating', () => {
  it('shows decision buttons to a fraud analyst', () => {
    renderWithRoles(['fraud_analyst'])
    expect(screen.getByRole('button', { name: 'Approve' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reject' })).toBeInTheDocument()
  })

  it('hides decision buttons from an ops viewer', () => {
    renderWithRoles(['ops_viewer'])
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.getByText(/permission to action cases/i)).toBeInTheDocument()
  })
})
