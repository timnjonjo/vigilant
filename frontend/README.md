# Vigilant — Admin Dashboard (Analyst Console)

Vigilant's own product surface (separate from the host bank's app) for fraud
analysts and ops. Two views per spec §8: **case management** and **aggregate
monitoring**. Runs against an in-memory **mock** by default — no backend needed.

## Stack

- **React 19 + Vite 7 + TypeScript**
- **Tailwind CSS v4** (CSS-first `@theme` tokens — the whole visual system is
  CSS variables, so a light theme later is a variable swap)
- **Radix UI** primitives (headless/accessible; styled entirely from our tokens
  so nothing reads as a template) + `class-variance-authority` + `lucide-react`
- **Recharts** — ops metrics
- **Cytoscape.js + dagre** — the analyst's subgraph explorer (chosen over vis.js
  for selector-based per-edge-type styling and directed/hierarchy layouts)

## Design language

- **Chrome is monochrome; saturated colour only ever means risk.** Score bands,
  case status, and alert severity all map through one source of truth
  (`src/lib/risk.ts`), so HIGH score / REJECT / "high" alert read identically.
- **All data is monospace** (scores, IDs, ASNs, KES, timestamps) — machine-truth.
- **Signature:** the segmented "signal bar" (`RiskBar`), repeated across queue
  rows, the case header, and ops metrics.
- Dark-first; responsive to tablet width.

## Run

```bash
npm install
cp .env.example .env
npm run dev        # http://localhost:5173  (mock data, no backend needed)
```

`npm run build` type-checks (`tsc -b`) and builds. The heavy routes (Cytoscape,
Recharts) are code-split and load on demand.

### `EMFILE: too many open files, watch …` on `npm run dev`?

Linux ran out of **inotify instances** (default `fs.inotify.max_user_instances`
is only 128, easily exhausted by IDEs/browsers). Two fixes:

- **No sudo, right now:** `npm run dev:poll` (polls instead of using inotify).
- **Permanent (recommended):** raise the limit once —
  ```bash
  echo 'fs.inotify.max_user_instances=1024' | sudo tee /etc/sysctl.d/99-inotify.conf
  echo 'fs.inotify.max_user_watches=524288' | sudo tee -a /etc/sysctl.d/99-inotify.conf
  sudo sysctl --system
  ```
  then plain `npm run dev` works.

## Structure

```
src/
├─ api/
│  ├─ index.ts        ← THE swap point: mock vs. real backend (VITE_USE_MOCK)
│  ├─ contract.ts     ← VigilantApi — the one interface both sides implement
│  ├─ mock/           ← in-memory fixtures + latency; case state mutates here
│  └─ http/           ← real transport (spec §7 endpoints)
├─ components/         ← shell + design primitives (RiskBar, RiskBadge, …)
├─ features/
│  ├─ cases/           ← queue, case detail, GraphExplorer, actions, audit
│  └─ monitoring/      ← fraud-rate chart, top codes, alerts
├─ lib/                ← risk (source of truth), format, labels, useAsync
└─ state/tenant.tsx    ← cosmetic tenant switch (spec §10 undecided)
```

## Swapping the mock for the real backend

Set `VITE_USE_MOCK=false`. `api/index.ts` then uses the HTTP transport and the
app requires Keycloak login. Note the **forward contracts** the backend must grow
(spec §8, marked in `types/api.ts` and `contract.ts`): `GET /v1/cases/{id}/graph`,
`/cases/{id}/audit`, and `GET /v1/monitoring`. Case list/detail/resolve already
exist (spec §7).

## Auth

**Keycloak OIDC** via `react-oidc-context` (redirect login, PKCE, refresh-token
silent renew, end-session logout). The access token is attached as `Bearer` to
every backend call; tenant + roles come from the token (`tenant_id`,
`realm_access.roles`). Role-aware UI (nav, case actions) is convenience only —
the **backend enforces** roles and tenant isolation.

**Mock mode bypasses Keycloak entirely** (the default), so UI dev needs no auth
server. For real login: `docker compose --profile auth up`, run the backend, then
`VITE_USE_MOCK=false npm run dev`. Seed logins (password `password`):
`analyst-loob`, `ops-loob`, `analyst-acme` — see `infra/keycloak/README.md`.

## Out of scope (this pass)

Social/federated login, fine-grained per-field permissions, and host write-back
on Approve/Hold/Reject — actions update case state locally only (spec §10).
