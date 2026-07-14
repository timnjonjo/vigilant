// Vigilant load harness — 200+ TPS audit.
//
// Exercises the two host-driven hot endpoints against a large synthetic graph
// (see perf/README.md). One script, three scenarios selected by env var:
//   SCENARIO = redemption | payout | blended   (default: blended)
//
// A client-credentials host_integration token is fetched once in setup() (the
// realistic auth path — local JWKS validation on the app side) and shared to all
// VUs. Codes/campaigns are chosen to match the seeded referrer→campaign mapping
// so the graph traversal does real work, not empty lookups.
//
// Run: k6 run -e SCENARIO=payout -e VUS=50 -e DURATION=30s perf/k6/load.js
import http from 'k6/http'
import { check } from 'k6'
import { Counter } from 'k6/metrics'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'
const KC = __ENV.KC_URL || 'http://localhost:8081'
const SCENARIO = __ENV.SCENARIO || 'blended'
const VUS = parseInt(__ENV.VUS || '50', 10)
const DURATION = __ENV.DURATION || '30s'
const REFERRERS = parseInt(__ENV.REFERRERS || '20000', 10)
const HOST_CLIENT_SECRET = __ENV.HOST_CLIENT_SECRET
const CAMPAIGNS = ['camp-a', 'camp-b', 'camp-c', 'camp-d']

const errors = new Counter('app_errors')

export const options = {
  scenarios: {
    [SCENARIO]: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      gracefulStop: '10s',
    },
  },
  // Percentiles we care about for the report.
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
}

export function setup() {
  if (!HOST_CLIENT_SECRET) throw new Error('HOST_CLIENT_SECRET is required')
  const res = http.post(
    `${KC}/realms/vigilant/protocol/openid-connect/token`,
    { grant_type: 'client_credentials', client_id: 'loob-bank-host', client_secret: HOST_CLIENT_SECRET },
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
  )
  if (res.status !== 200) throw new Error(`token fetch failed: ${res.status} ${res.body}`)
  return { token: res.json('access_token') }
}

function headers(token) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } }
}

// Referrer i was seeded with code LOOB-i on campaign CAMPAIGNS[i % 4]. Bias 10%
// of picks into the dense fraud rings (referrers 0..199) so the costly traversal
// path is represented, not just the sparse tail.
function pickReferrer() {
  if (Math.random() < 0.1) return Math.floor(Math.random() * 200)
  return Math.floor(Math.random() * REFERRERS)
}

function redemption(token) {
  const i = pickReferrer()
  const uid = `lt-${__VU}-${__ITER}`
  const body = {
    tenantId: 'loob-bank',
    campaignId: CAMPAIGNS[i % 4],
    referralCode: `LOOB-${i}`,
    newUserId: uid,
    deviceId: `d-${uid}`,
    ipAddress: `41.90.${i % 256}.${__ITER % 256}`,
    timestamp: null,
  }
  const res = http.post(`${BASE}/v1/events/redemption`, JSON.stringify(body), headers(token))
  if (!check(res, { 'redemption 200': (r) => r.status === 200 })) errors.add(1)
}

function payout(token) {
  const i = pickReferrer()
  const body = {
    tenantId: 'loob-bank',
    campaignId: CAMPAIGNS[i % 4],
    referralCode: `LOOB-${i}`,
    refereeUserId: `ree-${i}-1`,
  }
  const res = http.post(`${BASE}/v1/decisions/payout-check`, JSON.stringify(body), headers(token))
  if (!check(res, { 'payout 200': (r) => r.status === 200 })) errors.add(1)
}

export default function (data) {
  const t = data.token
  if (SCENARIO === 'redemption') redemption(t)
  else if (SCENARIO === 'payout') payout(t)
  else {
    // Blended: redemption is higher-volume than payout-check (fires at signup,
    // not just conversion). ~4:1 redemption:payout.
    if (__ITER % 5 === 0) payout(t)
    else redemption(t)
  }
}
