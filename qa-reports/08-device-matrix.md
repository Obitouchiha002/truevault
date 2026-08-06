# 08 — Device matrix

What TrueVault was actually run on, and — more importantly — what it was not.

## Executed

| Device | API | Type | ABI | Result |
|---|---|---|---|---|
| `sg34` (AVD, Pixel 5 profile, google_apis) | 34 | Emulator | x86_64 | Instrumented suite executed — see [07-instrumented-test-report.md](07-instrumented-test-report.md) |

Boot flags that made the emulator usable on this host, recorded because two earlier attempts timed
out at 600 s before they were applied:

```
-gpu swiftshader_indirect -no-snapshot-save -memory 3072 -cores 2
```

## NOT RUN — ENVIRONMENT UNAVAILABLE

| Target | API | Why it could not run |
|---|---|---|
| Any physical device | — | None attached. `adb devices` lists only the emulator. |
| Android 15 / 16 (Modern mode) | 35, 36 | **No API 35+ system image is installed.** Everything behind `TrueVaultProductMode.MODERN` — Private Space detection, private-app listing, the guided setup flow — has never executed. |
| minSdk floor | 26, 27 | No API 26/27 system image installed. The oldest supported configuration is untested. |
| API 28–33 | 28–33 | No system images installed. |
| `sg` (AVD, API 30) | 30 | Image is installed but the suite was not run on it during this pass. |
| StrongBox-backed Keystore | — | The emulator reports no StrongBox, so `setIsStrongBoxBacked(true)` has never taken the hardware path. |
| Samsung / Xiaomi / Oppo skins | — | No OEM devices or images available. The OEM guidance screens are untested against the settings intents they try to resolve. |
| Work profile / managed device | — | No provisioned work profile available. `ManagedProfileProvider` results are untested. |
| Tablet / foldable / large screen | — | No such AVD created. Adaptive layouts are unverified. |

## Consequence for the release verdict

Two of the app's headline behaviours — Modern-mode Private Apps, and correct degradation on the
oldest supported Android — are supported by **zero executed tests**. `productModeFor()` is unit
tested across API 26–40 so the *branch* is proven, but what happens on the other side of the API 35
branch has never run.

This is why [FINAL_TEST_REPORT.md](../FINAL_TEST_REPORT.md) records the verdict as conditional. It is
not a matter of confidence in the code; it is that the evidence does not exist yet.

## Minimum matrix before a public release

1. One API 35+ device or emulator image — otherwise Modern mode ships unexecuted.
2. One API 26–28 device or image — otherwise the minSdk claim is untested.
3. One physical device with real biometric hardware — enrolment, invalidation, and StrongBox.
4. One OEM skin (Samsung or Xiaomi) — SAF, MediaStore delete dialogs, and background killing.
