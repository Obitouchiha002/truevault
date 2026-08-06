# 04 — Static analysis

## Executed

### Android Lint

| Run | Task | Issues found |
|---|---|---|
| Debug | `:app:lintDebug` | **0** |
| Release | `:app:lintRelease` | **0** |

Raw XML: `qa-reports/lint/lint-results-debug.xml`, `qa-reports/lint/lint-results-release.xml`.
Both files contain zero `<issue>` elements — no errors, no warnings, no informational findings.

`lintVitalRelease` also runs as part of `assembleRelease` and passes; a fatal-severity lint issue
would have failed the release build outright.

Lint configuration is applied centrally by the convention plugins (`configureLint`), so every module
is analysed under the same rules rather than each one deciding for itself.

### Kotlin compiler

Compiles clean under Kotlin 2.3.21 with:

```
-Xjsr305=strict
-Xconsistent-data-class-copy-visibility
-opt-in=kotlin.RequiresOptIn
-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi
```

`allWarningsAsErrors` is **false**. Turning it on would be a real improvement and is not a
formality: it is how an accidental deprecated-API use or an unchecked cast stops being something
that scrolls past in a build log.

### R8

`minifyReleaseWithR8` succeeds. R8 performs its own reachability and shrinking analysis and did not
report unresolved references.

### Merged-manifest inspection

`aapt2 dump permissions` over the built release APK. This is static analysis of the *artefact*
rather than of the source, and it found something no source-level tool would have:
`ACCESS_NETWORK_STATE` and `WAKE_LOCK` merged in from media3. Both are now removed. See
[02-build-report.md](02-build-report.md).

**Recommendation:** make this dump a standing gate. Source review cannot see what manifest merging
adds, and permissions are precisely where a privacy claim quietly becomes untrue.

## NOT RUN

| Tool | Status | What it would have caught |
|---|---|---|
| detekt | **NOT RUN — NOT CONFIGURED.** Not in the build. | Complexity, long methods, code smells, Kotlin-specific rules |
| ktlint / spotless | **NOT RUN — NOT CONFIGURED.** | Formatting consistency. Currently enforced only by convention. |
| Android Lint baseline check | **NOT APPLICABLE.** No baseline file exists — and with zero issues, none is needed. A baseline would be a way to hide findings, not fix them. |
| MobSF / semgrep / security rulesets | **NOT RUN — NOT INSTALLED.** | Hardcoded secrets, insecure crypto patterns, exported-component issues |
| Dependency CVE scan | **NOT RUN — NOT INSTALLED.** See [03](03-dependency-inventory.md). | Known vulnerabilities in pinned versions |
| API-surface / binary-compatibility check | **NOT RUN.** | Accidental public API changes between modules |

## Honest reading of "0 lint issues"

Zero findings from Android Lint means the code passes the rules Lint actually has. It does not mean
the code is free of defects, and this pass is proof of that: the bugs found here — the Keystore IV
that broke vault creation on every device, backups that could never be restored, an infinite ticker
that hung the test suite, three permissions merged in from a library — were found by **running the
code**, not by analysing it. Not one of them would have been caught by any static analyser in the
list above.

Static analysis is a floor. Nothing in this report should be read as a substitute for the device
testing recorded as missing in [08-device-matrix.md](08-device-matrix.md).
