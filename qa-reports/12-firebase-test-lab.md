# 12 — Firebase Test Lab

## Status: NOT RUN — ENVIRONMENT UNAVAILABLE

No test was executed on Firebase Test Lab or any other device farm. The blockers are factual, not
matters of judgement:

| Requirement | Present? |
|---|---|
| `gcloud` CLI installed | No |
| Firebase / Google Cloud project | None configured |
| Service-account credentials | None available |
| Billing account | None |
| Network-authorised CI runner | Not present in this environment |

Nothing in this report may be read as a claim about behaviour on physical hardware. Every device
statement TrueVault can currently support comes from one emulator, and is recorded as such in
[08-device-matrix.md](08-device-matrix.md).

## What a Test Lab run would need to cover, once credentials exist

Recorded here so the gap is actionable rather than merely acknowledged. The matrix below is what
this project actually needs — not a generic sweep.

### Device matrix

| Tier | Device | API | Why this one |
|---|---|---|---|
| Modern | Pixel 8 / 9 | 35, 36 | The only tier where Private Space exists. Untestable locally: no API 35+ system image is installed. |
| Core — recent | Pixel 6 | 34 | The Core-mode reference |
| Core — mid | Samsung Galaxy A-series | 31–33 | One-UI storage and permission behaviour differs from AOSP in ways that have broken SAF flows before |
| Core — old | Any API 26–28 device | 26 | minSdk floor. Keystore behaviour, StrongBox absence, and scoped-storage-free file access all differ here. |
| OEM | Xiaomi / MIUI, Oppo / ColorOS | 33+ | Aggressive background killing and their own "private space" features, which the OEM guidance screens refer to |

### Test targets

1. `connectedDebugAndroidTest` — the existing instrumented suite, per device.
2. Robo test over onboarding → lock creation → import → unlock, to catch crashes on OEM skins.
3. A game-loop-style scripted run of Secure Move including the platform delete dialog, which cannot
   be automated meaningfully on an emulator without a real MediaStore corpus.

### What each tier would answer that the emulator cannot

- **Private Space** (Modern): every code path behind `PrivateAppsSupport` is currently unexercised.
- **StrongBox**: the emulator reports no StrongBox, so the `setIsStrongBoxBacked(true)` path has
  never actually executed against real secure hardware.
- **Biometric enrolment invalidation**: removing a fingerprint must invalidate the biometric key copy
  while leaving the password path intact. Emulator biometrics do not model this faithfully.
- **MediaStore delete requests**: the system confirmation dialog and its result codes differ between
  AOSP and OEM builds, and Secure Move's honesty depends on reading that result correctly.

Until these run, [FINAL_TEST_REPORT.md](../FINAL_TEST_REPORT.md) records the release verdict as
conditional on device testing, not as passed.
