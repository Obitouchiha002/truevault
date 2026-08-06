# 06 — Coverage report

**Executed.** `./gradlew koverXmlReport koverHtmlReport` — Kover 0.9.9, aggregated across every
subproject. Source: `build/reports/kover/report.xml`, copied into `qa-reports/coverage/`.

These numbers cover **unit tests only**. Instrumented tests do not contribute to this report, so
anything that can only run on a device reads as 0% here even where an emulator test exists.

## Totals

| Metric | Covered | Total | % |
|---|---|---|---|
| Line | 1 130 | 11 796 | **9.6 %** |
| Branch | 442 | 3 988 | **11.1 %** |
| Instruction | 7 644 | 93 180 | **8.2 %** |

9.6 % is a low number and this report is not going to dress it up. What matters is *which* 9.6 %.

## Where the coverage is

The security-critical core is the part that is tested:

| Package | Line coverage |
|---|---|
| `core.crypto.kdf` | **97.3 %** (36/37) |
| `core.crypto.aead` | **82.4 %** (28/34) |
| `core.crypto.file` — container format | **86.8 %** (164/189) |
| `core.crypto.session` | **79.6 %** (43/54) |
| `core.crypto.vault` — key manager | **61.5 %** (216/351) |
| `core.model` | **81.1 %** (253/312) |
| `core.data.model` | **67.6 %** (48/71) |
| `core.testing` | 83.1 % |

Every routine that decides whether a file can be decrypted, whether a password unlocks a vault, or
whether an item may be called "secured" is in that list.

## Where the coverage is not

| Package | Line coverage | Why |
|---|---|---|
| `core.database.dao` | **0 %** (0/1 572) | Room-generated DAO implementations. Reachable only from an instrumented test; the migration tests that do run are not counted here. |
| `feature.*.presentation` | **0 %** across ~3 400 lines | Compose UI. Only two ViewModel suites exist, in `feature.authentication` (12.5 %). |
| `core.designsystem` | 0 % (867 lines) | Compose components |
| `core.storage` | 0 % (293) | File-system code that needs a real Android context |
| `core.crypto.keystore` | **1 %** (1/100) | Android Keystore. **Cannot run on the JVM at all** — and this is the exact package where the device-breaking IV bug lived. |
| `core.capabilities.provider` | 0 % (187) | Needs a device |
| `core.data` | **2 %** (22/1 102) | `SecureImportEngine` and `PrivacyScanEngine` are the largest untested bodies of logic in the app |

## The two gaps that matter most

**1. `core.crypto.keystore` at 1 %.** This is not a coverage statistic, it is the same lesson twice.
The bug that made vault creation fail on every real device lived here, passed every unit test, and
was only ever going to be caught on a device. A JVM coverage number for this package is close to
meaningless; what it needs is instrumented tests, and it has almost none.

**2. `core.data` at 2 %.** `SecureImportEngine` holds the Secure Copy / Secure Move state machine —
the ordering that guarantees no original is deleted before its encrypted copy is verified. That
ordering is the app's central safety property and it is currently asserted by review, not by a test.
The engine is hard to unit test because it needs Room, a `ContentResolver` and `Uri`; that is a
reason it has not been done, not a reason it does not matter. Robolectric or an instrumented test
would both work.

Two smaller pieces were extracted during this pass specifically so they could be tested without that
machinery: `DeletionOutcome.resolve()` (the "original deleted" claim) and `candidateSources()` (the
scanner's pre-filter). Both are now covered exhaustively. The rest of the engine is not.

## What would move the number, in order of value

1. Instrumented tests for `core.crypto.keystore` — highest value per test in the whole project.
2. `SecureImportEngine` under Robolectric or on a device, especially the interrupted-import paths.
3. DAO tests in `core:database` androidTest.
4. ViewModel tests for the remaining features — mechanical, and would move the percentage most.

## No threshold is enforced

Kover is wired up and reporting, but no `koverVerify` rule fails the build on a minimum. Adding one
at today's 9.6 % would enforce nothing; adding one at a target the project does not meet would break
every build. The honest position is: the number is published, it is low, and the two gaps above are
recorded as release blockers in [FINAL_TEST_REPORT.md](../FINAL_TEST_REPORT.md) rather than hidden
behind a threshold that was set to whatever the code happened to score.
