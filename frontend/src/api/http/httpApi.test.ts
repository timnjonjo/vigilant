import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../auth/tokenStore'
import { httpApi } from './httpApi'

describe('httpApi case resolution', () => {
  afterEach(() => {
    tokenStore.set(null)
    vi.unstubAllGlobals()
  })

  it('does not send a client-selected audit actor', async () => {
    tokenStore.set('signed-access-token')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await httpApi.actOnCase('loob-bank', 42, 'REJECT', 'forged-super-admin')

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.headers).toMatchObject({ Authorization: 'Bearer signed-access-token' })
    expect(JSON.parse(String(init.body))).toEqual({
      tenantId: 'loob-bank',
      resolution: 'REJECT',
    })
  })
})
