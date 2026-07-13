import { render, screen } from '@testing-library/react'
import { useEffect } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RealSessionProvider } from './session'
import { tokenStore } from './tokenStore'

// One mutable auth state the mocked useAuth returns.
const signinRedirect = vi.fn()
let authState: Record<string, unknown>

vi.mock('react-oidc-context', () => ({
  useAuth: () => authState,
}))

function jwtWith(payload: object): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '')
  return `${b64({ alg: 'none' })}.${b64(payload)}.`
}

describe('RealSessionProvider (auth-gated routing)', () => {
  beforeEach(() => signinRedirect.mockClear())

  it('redirects an unauthenticated user to Keycloak login', () => {
    authState = { isLoading: false, error: null, isAuthenticated: false, user: null, signinRedirect }

    render(
      <RealSessionProvider>
        <div>dashboard</div>
      </RealSessionProvider>,
    )

    expect(signinRedirect).toHaveBeenCalled()
    expect(screen.queryByText('dashboard')).not.toBeInTheDocument()
  })

  it('renders the dashboard for an authenticated user and exposes token claims', () => {
    const token = jwtWith({
      tenant_id: 'loob-bank',
      realm_access: { roles: ['fraud_analyst'] },
      preferred_username: 'amina',
    })
    authState = {
      isLoading: false,
      error: null,
      isAuthenticated: true,
      user: { access_token: token, profile: { preferred_username: 'amina' } },
      signinRedirect,
    }

    render(
      <RealSessionProvider>
        <div>dashboard</div>
      </RealSessionProvider>,
    )

    expect(screen.getByText('dashboard')).toBeInTheDocument()
    expect(signinRedirect).not.toHaveBeenCalled()
  })

  it('populates the bearer token before a child effect fires (no 401 loop)', () => {
    // Child effects run before parent effects, so if the provider stored the token
    // in an effect, the child's first request would race ahead of it, 401, and
    // trigger a re-login loop. The token must be set synchronously during render.
    const token = jwtWith({ tenant_id: 'loob-bank', realm_access: { roles: [] } })
    authState = {
      isLoading: false,
      error: null,
      isAuthenticated: true,
      user: { access_token: token, profile: {} },
      signinRedirect,
    }
    tokenStore.set(null)
    let tokenSeenByChildEffect: string | null = 'UNSET'
    function Child() {
      useEffect(() => {
        tokenSeenByChildEffect = tokenStore.get()
      }, [])
      return <div>dashboard</div>
    }

    render(
      <RealSessionProvider>
        <Child />
      </RealSessionProvider>,
    )

    expect(tokenSeenByChildEffect).toBe(token)
  })

  it('shows an actionable error (not a blank page) when sign-in fails', () => {
    authState = {
      isLoading: false,
      error: { message: 'network error' },
      isAuthenticated: false,
      user: null,
      signinRedirect,
    }

    render(
      <RealSessionProvider>
        <div>dashboard</div>
      </RealSessionProvider>,
    )

    expect(screen.getByText(/couldn’t sign in/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
  })
})
