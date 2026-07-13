import type {
  AuditEntry,
  Campaign,
  CaseGraph,
  CaseView,
  MonitoringSummary,
  Tenant,
} from '../../types/api'

export const TENANTS: Tenant[] = [
  { id: 'loob-bank', name: 'Loob Bank' },
  { id: 'acme-sacco', name: 'Acme Sacco' },
]

const BASE = new Date('2026-07-10T09:00:00Z').getTime()
const hoursAgo = (h: number) => new Date(BASE - h * 3_600_000).toISOString()
const daysAgo = (d: number) => new Date(BASE - d * 86_400_000).toISOString().slice(0, 10)

/** Two campaigns per tenant — one ACTIVE, one ENDED (spec §10a). */
export function seedCampaigns(): Campaign[] {
  const mk = (
    campaignId: string,
    tenantId: string,
    name: string,
    bonusAmount: number,
    status: Campaign['status'],
    conversionCriteria: Campaign['conversionCriteria'],
  ): Campaign => ({
    campaignId,
    tenantId,
    name,
    bonusAmount,
    startDate: daysAgo(status === 'ENDED' ? 90 : 20),
    endDate: status === 'ENDED' ? daysAgo(10) : daysAgo(-40),
    status,
    conversionCriteria,
    referralCapPerUser: status === 'ENDED' ? 3 : 5,
    createdAt: hoursAgo(status === 'ENDED' ? 2160 : 480),
    updatedAt: hoursAgo(4),
  })
  return [
    mk('loob-q3', 'loob-bank', 'Q3 Signup Boost', 350, 'ACTIVE', 'FIRST_DEPOSIT'),
    mk('loob-jielimishe', 'loob-bank', 'Jielimishe Promo', 200, 'ENDED', 'N_DAY_RETENTION'),
    mk('acme-boost', 'acme-sacco', 'Member Boost', 300, 'ACTIVE', 'FIRST_DEPOSIT'),
    mk('acme-legacy', 'acme-sacco', 'Launch Referral', 150, 'ENDED', 'FIRST_DEPOSIT'),
  ]
}

/** Seed cases — deep-cloned per session so mutations don't leak across reloads. */
export function seedCases(): CaseView[] {
  return [
    {
      id: 1001,
      tenantId: 'loob-bank',
      campaignId: 'loob-q3',
      referralCode: 'LOOB-9F3A',
      refereeUserId: 'usr_71134',
      decision: 'REJECT',
      score: 0.9,
      reasonCodes: ['DEVICE_COLLISION', 'IP_SUBNET_COLLISION'],
      status: 'OPEN',
      resolution: null,
      resolvedBy: null,
      openedAt: hoursAgo(2),
      resolvedAt: null,
    },
    {
      id: 1002,
      tenantId: 'loob-bank',
      campaignId: 'loob-q3',
      referralCode: 'LOOB-22C7',
      refereeUserId: 'usr_80512',
      decision: 'HOLD',
      score: 0.45,
      reasonCodes: ['DATACENTER_OR_VPN_IP'],
      status: 'OPEN',
      resolution: null,
      resolvedBy: null,
      openedAt: hoursAgo(6),
      resolvedAt: null,
    },
    {
      id: 1003,
      tenantId: 'loob-bank',
      campaignId: 'loob-q3',
      referralCode: 'LOOB-1B08',
      refereeUserId: 'usr_66240',
      decision: 'REJECT',
      score: 0.85,
      reasonCodes: ['VELOCITY_BURST', 'CYCLE_DETECTED'],
      status: 'OPEN',
      resolution: null,
      resolvedBy: null,
      openedAt: hoursAgo(19),
      resolvedAt: null,
    },
    {
      id: 1004,
      tenantId: 'acme-sacco',
      campaignId: 'acme-boost',
      referralCode: 'ACME-4410',
      refereeUserId: 'mbr_03318',
      decision: 'HOLD',
      score: 0.5,
      reasonCodes: ['VELOCITY_BURST'],
      status: 'OPEN',
      resolution: null,
      resolvedBy: null,
      openedAt: hoursAgo(27),
      resolvedAt: null,
    },
    {
      id: 1005,
      tenantId: 'loob-bank',
      campaignId: 'loob-jielimishe',
      referralCode: 'LOOB-77E1',
      refereeUserId: 'usr_51900',
      decision: 'HOLD',
      score: 0.42,
      reasonCodes: ['IP_SUBNET_COLLISION'],
      status: 'RESOLVED',
      resolution: 'APPROVE',
      resolvedBy: 'amina.k',
      openedAt: hoursAgo(52),
      resolvedAt: hoursAgo(48),
    },
  ]
}

/** Node/edge helpers keep the fixture graphs terse. */
const n = (userId: string, role: 'referrer' | 'referee', ipType: CaseGraph['nodes'][number]['ipType'] = 'RESIDENTIAL', converted = false) => ({
  id: userId,
  userId,
  role,
  ipType,
  converted,
})
const e = (source: string, target: string, type: CaseGraph['edges'][number]['type']) => ({
  id: `${source}-${type}-${target}`,
  source,
  target,
  type,
})

export const CASE_GRAPHS: Record<number, CaseGraph> = {
  // Device + subnet cluster: three referees sharing a device and a /24.
  1001: {
    nodes: [
      n('usr_referrer_1001', 'referrer'),
      n('usr_71134', 'referee', 'RESIDENTIAL', true),
      n('usr_71135', 'referee'),
      n('usr_71136', 'referee', 'RESIDENTIAL', true),
    ],
    edges: [
      e('usr_referrer_1001', 'usr_71134', 'REFERRED'),
      e('usr_referrer_1001', 'usr_71135', 'REFERRED'),
      e('usr_referrer_1001', 'usr_71136', 'REFERRED'),
      e('usr_71134', 'usr_71135', 'SHARES_DEVICE'),
      e('usr_71135', 'usr_71136', 'SHARES_DEVICE'),
      e('usr_71134', 'usr_71136', 'SHARES_IP_SUBNET'),
    ],
  },
  // Datacenter referee.
  1002: {
    nodes: [
      n('usr_referrer_1002', 'referrer'),
      n('usr_80512', 'referee', 'DATACENTER', true),
    ],
    edges: [e('usr_referrer_1002', 'usr_80512', 'REFERRED')],
  },
  // Fan-out burst that closes a cycle back to the referrer.
  1003: {
    nodes: [
      n('usr_66240', 'referrer'),
      ...Array.from({ length: 7 }, (_, i) => n(`usr_r3_${i}`, 'referee', 'RESIDENTIAL', i < 2)),
    ],
    edges: [
      ...Array.from({ length: 7 }, (_, i) => e('usr_66240', `usr_r3_${i}`, 'REFERRED')),
      e('usr_r3_0', 'usr_66240', 'REFERRED'), // cycle
    ],
  },
  // Simple fan-out burst (Acme).
  1004: {
    nodes: [
      n('mbr_03318', 'referrer'),
      ...Array.from({ length: 9 }, (_, i) => n(`mbr_r4_${i}`, 'referee')),
    ],
    edges: Array.from({ length: 9 }, (_, i) => e('mbr_03318', `mbr_r4_${i}`, 'REFERRED')),
  },
  1005: {
    nodes: [n('usr_referrer_1005', 'referrer'), n('usr_51900', 'referee', 'RESIDENTIAL', true)],
    edges: [e('usr_referrer_1005', 'usr_51900', 'SHARES_IP_SUBNET')],
  },
}

export const SEED_AUDIT: Record<number, AuditEntry[]> = {
  1001: [{ id: 'a1001-0', at: hoursAgo(2), actor: 'engine', action: 'OPENED', note: 'Score 0.90 → REJECT (device + subnet collision).' }],
  1002: [{ id: 'a1002-0', at: hoursAgo(6), actor: 'engine', action: 'OPENED', note: 'Score 0.45 → HOLD (datacenter IP).' }],
  1003: [{ id: 'a1003-0', at: hoursAgo(19), actor: 'engine', action: 'OPENED', note: 'Score 0.85 → REJECT (velocity burst + cycle).' }],
  1004: [{ id: 'a1004-0', at: hoursAgo(27), actor: 'engine', action: 'OPENED', note: 'Score 0.50 → HOLD (velocity burst).' }],
  1005: [
    { id: 'a1005-0', at: hoursAgo(52), actor: 'engine', action: 'OPENED', note: 'Score 0.42 → HOLD (subnet collision).' },
    { id: 'a1005-1', at: hoursAgo(48), actor: 'amina.k', action: 'APPROVE', note: 'Same household, verified genuine. False positive.' },
  ],
}

export function buildMonitoring(tenantId: string): MonitoringSummary {
  const days = 30
  const series = Array.from({ length: days }, (_, i) => {
    const date = new Date(BASE - (days - 1 - i) * 86_400_000).toISOString().slice(0, 10)
    // Deterministic wave with a late-window spike (a "baseline shift" to alert on).
    const wave = 2.4 + Math.sin(i / 3) * 0.8 + (i > 24 ? (i - 24) * 0.6 : 0)
    return {
      date,
      flaggedRate: Number(wave.toFixed(2)),
      reviewedRate: Number((wave * 0.62).toFixed(2)),
    }
  })

  if (tenantId === 'acme-sacco') {
    return {
      blockedValueKes: 412_500,
      blockedValueTrendPct: 8.1,
      falsePositiveRatePct: 11.4,
      falsePositiveTrendPct: -1.2,
      openCaseCount: 1,
      fraudRateSeries: series,
      topCodes: [
        { referralCode: 'ACME-4410', tenantId, volume: 214, riskScore: 0.5, blockedKes: 96_000 },
        { referralCode: 'ACME-2231', tenantId, volume: 178, riskScore: 0.28, blockedKes: 0 },
        { referralCode: 'ACME-7756', tenantId, volume: 141, riskScore: 0.19, blockedKes: 0 },
      ],
      alerts: [
        { id: 'al-a1', severity: 'medium', title: 'Fan-out above baseline', detail: 'ACME-4410 issued 9 referrals in 40 minutes.', at: hoursAgo(27) },
      ],
    }
  }

  return {
    blockedValueKes: 1_284_000,
    blockedValueTrendPct: 23.7,
    falsePositiveRatePct: 6.8,
    falsePositiveTrendPct: -2.4,
    openCaseCount: 3,
    fraudRateSeries: series,
    topCodes: [
      { referralCode: 'LOOB-9F3A', tenantId, volume: 62, riskScore: 0.9, blockedKes: 540_000 },
      { referralCode: 'LOOB-1B08', tenantId, volume: 51, riskScore: 0.85, blockedKes: 447_000 },
      { referralCode: 'LOOB-22C7', tenantId, volume: 33, riskScore: 0.45, blockedKes: 120_000 },
      { referralCode: 'LOOB-5521', tenantId, volume: 210, riskScore: 0.22, blockedKes: 0 },
      { referralCode: 'LOOB-77E1', tenantId, volume: 27, riskScore: 0.42, blockedKes: 0 },
    ],
    alerts: [
      { id: 'al-l1', severity: 'high', title: 'Flagged-rate baseline shift', detail: 'Daily flagged rate up 41% over the trailing 5-day mean.', at: hoursAgo(5) },
      { id: 'al-l2', severity: 'critical', title: 'Device cluster expanding', detail: 'Shared-device ring around LOOB-9F3A grew to 4 accounts.', at: hoursAgo(2) },
      { id: 'al-l3', severity: 'low', title: 'New datacenter ASN seen', detail: 'First redemption from AS14061 (DigitalOcean) this week.', at: hoursAgo(6) },
    ],
  }
}
