import { describe, expect, it } from 'vitest'
import { mockApi } from './mockApi'

// The mock is the contract stand-in for the real backend; its case search must
// behave like the Postgres one — match a referral code or referee user id,
// case-insensitively — so screens built against the mock work unchanged when swapped.
describe('mockApi case search', () => {
  const tenantId = 'loob-bank'

  it('matches a referral code or referee user id, case-insensitively', async () => {
    const all = await mockApi.listCases({ tenantId })
    expect(all.items.length).toBeGreaterThan(0)
    const sample = all.items[0]

    const byCode = await mockApi.listCases({
      tenantId,
      search: sample.referralCode.toUpperCase(),
    })
    expect(byCode.items.map((c) => c.id)).toContain(sample.id)
    expect(
      byCode.items.every(
        (c) =>
          c.referralCode.toLowerCase().includes(sample.referralCode.toLowerCase()) ||
          c.refereeUserId.toLowerCase().includes(sample.referralCode.toLowerCase()),
      ),
    ).toBe(true)

    const byReferee = await mockApi.listCases({ tenantId, search: sample.refereeUserId })
    expect(byReferee.items.map((c) => c.id)).toContain(sample.id)
  })

  it('returns nothing for a term that matches no code or user id', async () => {
    const none = await mockApi.listCases({ tenantId, search: 'zzz-definitely-no-match' })
    expect(none.items).toEqual([])
    expect(none.nextCursor).toBeNull()
  })
})
