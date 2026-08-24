# Heraldor 1.0 release evidence

This document records the evidence currently available for Heraldor `1.0.0`.
It is not a complete release sign-off: the unchecked gates below still require
runtime evidence before the release can be called finished.

## Reproducible artifact

| Field | Recorded value |
| --- | --- |
| Version | `1.0.0` |
| Artifact | `zapeg-runtime-forge-1.20.1-1.0.0.jar` |
| Size | `1,144,742` bytes |
| SHA-256 | `BC9A74A7B07376299DD6FCE4EF3654911559A0CAC481A65596921FCE53BDD903` |
| Automated tests | `522` tests across `126` suites |

Two clean builds completed successfully. Their release JARs were byte-for-byte
identical, including the recorded size and SHA-256 above. This establishes exact
reproducibility for those two builds; downstream server, client and update
copies must still be checked against the same hash when they are installed.

## Dedicated-server smoke evidence

A dedicated server using Forge `47.4.10` reached the `Done` state. A subsequent
resource reload completed successfully, and an unauthorized summon was
rejected. This is a focused Forge smoke result, not the pending ATM9 or
multiplayer sign-off.

Heraldor commands issued from the local server console are intentionally
rejected by `CommandSourcePolicy`. The trusted operator shapes are a direct
permission-level-2 player and authenticated RCON; command blocks, functions,
local console and redirected operator identities are disallowed. Accordingly,
local-console rejection is expected policy behavior and does not satisfy the
separate RCON gate.

## Release gate checklist

- [x] Version set to `1.0.0`.
- [x] Two clean builds completed.
- [x] Automated result recorded: `522` tests in `126` suites.
- [x] Both clean builds produced the exact artifact size and SHA-256 above.
- [x] Dedicated Forge `47.4.10` server reached `Done`.
- [x] Dedicated-server resource reload completed.
- [x] Unauthorized summon was rejected.
- [ ] Exercise the authenticated RCON operator paths.
- [ ] Complete the ATM9 dedicated-server smoke gate.
- [ ] Complete the two-client visual and privacy gate.
- [ ] Verify the supported OS-level window-shake path and its cleanup behavior.

The unchecked items are release blockers. None is implied to pass by the clean
build, automated-test, reproducibility or focused Forge smoke evidence above.
