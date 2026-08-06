# 11 — Performance

## Executed

### Unit-test wall times

Taken from the JUnit XML in `qa-reports/junit/`, so they measure the JVM implementation, not device
behaviour.

| Suite | Tests | Time |
|---|---|---|
| `VaultKeyManagerTest` | 22 | 9.77 s — dominated by real Argon2id derivations, which is the point |
| `BackupRekeyingTest` | 4 | 4.58 s |
| `AesGcmTest` | 11 | 0.95 s |
| `SyntheticCorpusRoundTripTest` | 7 | 0.86 s |
| `VaultFileCipherTest` | 23 | 0.36 s |

Argon2id being slow *is* the security property. The figure worth watching is the per-derivation
cost, and the reason it appears here at all is so that a future change which quietly weakens the KDF
parameters shows up as a suite that suddenly got fast.

### Design decisions made for memory, and where they are enforced

| Path | Approach | Peak memory |
|---|---|---|
| Import encryption | Streaming, `DEFAULT_CHUNK_SIZE` chunks | Bounded by chunk size, not file size |
| Backup export/restore | 64 KiB streaming with a running SHA-256 | ~64 KiB per file |
| Container verification | Streamed read-back before commit | Bounded |
| Import queue | Strictly serial, one file at a time | One file's working set |

The backup path was **not** always like this. It called `readBytes()` on whole containers, which
would have thrown `OutOfMemoryError` on the first large video anyone tried to back up. That was found
and fixed during this pass; the streaming version is what the tests now exercise.

## NOT RUN — ENVIRONMENT UNAVAILABLE

Every measurement below needs a device. None was available, and no number for any of them appears
anywhere in this repository.

| Metric | Status |
|---|---|
| Cold start / warm start (`am start -W`, Macrobenchmark) | **NOT RUN** |
| Jank / frame timing on scroll (`JankStats`, Perfetto) | **NOT RUN** |
| Encryption throughput MB/s on real storage | **NOT RUN** |
| Import of a 1 GB video end to end | **NOT RUN** |
| Battery drain during a large import | **NOT RUN** |
| Memory profile under a 10 000-item vault | **NOT RUN** |
| APK / AAB size breakdown | **NOT MEASURED** — the release artefact was built (see [02-build-report.md](02-build-report.md)) but not analysed |
| Baseline Profile generation | **NOT DONE.** No baseline profile is included, so first-run performance is unoptimised. |

`qa-reports/benchmarks/` is empty, and it is empty because nothing was measured — not because the
measurements were clean.

## Recommendation before release

1. Add a Macrobenchmark module and generate a Baseline Profile. On a mid-range device this is usually
   the single largest first-run improvement available.
2. Measure encryption throughput on real flash storage. The chunk size is currently chosen on
   reasoning, not on measurement, and the two do not always agree.
3. Profile the vault grid with a synthetic 10 000-item corpus — `SyntheticTestData` can generate it,
   and Paging 3 is already wired in, but neither has been exercised at that scale.
