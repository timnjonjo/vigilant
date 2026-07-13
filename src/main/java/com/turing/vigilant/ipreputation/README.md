# ip-reputation module (Tier 1)

Fast, locally-hosted, zero-network IP reputation. Resolves an IP's ASN from a
local MaxMind-format `.mmdb` and classifies it as `RESIDENTIAL`, `MOBILE`,
`DATACENTER`, or `UNKNOWN`. A datacenter/VPN/cloud IP becomes the
`DATACENTER_OR_VPN_IP` scoring signal.

Tier 2 (a paid external checker such as IPQualityScore) is deliberately **out of
scope** here — separate module, separate prompt.

## Data source: DB-IP IP-to-ASN Lite (not MaxMind)

The original design named MaxMind GeoLite2-ASN. We switched the **data source**
to **[DB-IP IP-to-ASN Lite](https://db-ip.com/db/download/ip-to-asn-lite)** while
keeping the open **`.mmdb` format** and the MaxMind `maxmind-db` reader library
(the reader is source-agnostic). Rationale:

| | DB-IP IP-to-ASN Lite | IPinfo Lite |
|---|---|---|
| License | **CC-BY 4.0** (attribution only) | CC-BY-**SA** 4.0 (copyleft) |
| Account/token | **None** | Required |
| Refresh | Monthly | Daily |

CC-BY (no ShareAlike) is the safer license for a commercial engine, and the
tokenless direct download makes the automated refresh trivial. **Attribution:**
credit `DB-IP.com` per CC-BY 4.0.

The reader is field-name tolerant (`autonomous_system_number` / `as_number` /
`asn`), so GeoLite2 or IPinfo `.mmdb` files also work if you prefer them.

## Provisioning

**Automated (recommended)** — the `geoip` service in `docker-compose.yml`
downloads and gunzips the current monthly file into a shared volume, then the
`vigilant` service mounts it read-only at `/data/geoip`:

```bash
docker compose --profile full up --build        # geoip runs, then the service starts
docker compose run --rm geoip                    # monthly refresh
```

**Manual** — download `dbip-asn-lite-YYYY-MM.mmdb.gz` from the DB-IP link above,
gunzip it, and point config at the file:

```bash
export VIGILANT_ASN_DB=/path/to/dbip-asn-lite.mmdb
```

The service **fails fast at startup** if the database is missing or unreadable —
it never starts silently degraded.

## Configuration (`vigilant.ip-reputation.*`)

| Property | Meaning |
|---|---|
| `database-path` | Path to the `.mmdb` (env `VIGILANT_ASN_DB`) |
| `datacenter-asns` | Cloud/hosting ASNs → `DATACENTER`. Extend without code changes. |
| `kenyan-carrier-asns` | Known-good mobile ASNs → `MOBILE` (allowlist, checked first) |

Lists are config-driven so they extend without a redeploy of code. A
Postgres-backed table (read on a TTL cache) is the later upgrade for
no-restart updates; not built yet.

> ⚠️ **Verify the ASN numbers before production.** The datacenter list covers
> the major providers (AWS, GCP, Azure, DigitalOcean, Linode, OVH, Hetzner,
> Vultr, Alibaba). The **Kenyan carrier allowlist** (Safaricom, Airtel Kenya,
> Telkom Kenya) short-circuits false positives on CGNAT'd mobile ranges — but
> carrier ASNs change; confirm against current BGP data. Safaricom `AS33771` is
> the confident one.

## How it plugs in

- **Ingestion** (`/v1/events/redemption`, code issuance): the checker runs and
  the result is stored on the `Account` node (`ipType`, `ipReputationCheckedAt`).
  Soft-flag only — ingestion is never gated (spec: never block signup).
- **Payout** (`/v1/decisions/payout-check`): `DatacenterIpRule` reads the stored
  `ipType` from the neighbourhood — graph-pure, no live lookup at score time —
  and contributes `datacenterWeight` (0.70) with reason `DATACENTER_OR_VPN_IP`.
  0.70 alone lands in HOLD; combined with another signal it tips into REJECT.

## Test fixture

`MmdbAsnResolverIT` self-skips unless a real database is present at
`src/test/resources/ipreputation/dbip-asn-lite-sample.mmdb`. Drop a small
DB-IP `.mmdb` there to exercise the real reader binding. Do **not** commit the
production database. The classification logic is fully covered without any file
via `LocalAsnReputationCheckerTest` (stub resolver).
