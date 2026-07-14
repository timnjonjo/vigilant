# Local security regression PoCs

These scripts refuse non-loopback application URLs. They require short-lived
tokens/environment variables and never contain credentials. Run only against a
disposable local/dev data set.

`payout_fail_open.sh`, `cross_campaign_code.sh`, `input_validation.sh`, and
`forged_audit_actor.sh` reproduce the confirmed API findings and now verify the
fixes. `config_hardening.sh` checks the configuration fixes.

`rate_limit_probe.sh` intentionally still exits non-zero: it characterises the
open rate-limit control gap until tenant throughput budgets and the production
gateway implementation are selected.
