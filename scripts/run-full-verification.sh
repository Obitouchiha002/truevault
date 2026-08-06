#!/usr/bin/env bash
#
# TrueVault full verification run.
#
# Exits non-zero when any mandatory gate fails, so it can be wired into CI unchanged.
# No machine-specific absolute paths: JAVA_HOME and ANDROID_HOME are discovered or taken from the
# environment, and everything else is relative to the repository root.
#
# Usage:
#   scripts/run-full-verification.sh              # everything available
#   scripts/run-full-verification.sh --no-device  # skip connected tests even if a device is present
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

REPORTS="qa-reports"
SKIP_DEVICE=0
[[ "${1:-}" == "--no-device" ]] && SKIP_DEVICE=1

mkdir -p "$REPORTS"/{junit,lint,coverage,benchmarks,screenshots}

# ---------------------------------------------------------------------------------------------
# Tool discovery
# ---------------------------------------------------------------------------------------------
if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "${HOME}/.sdkman/candidates/java/current"; do
    [[ -x "$candidate/bin/java" ]] && export JAVA_HOME="$candidate" && break
  done
fi
if [[ -z "${JAVA_HOME:-}" ]] && command -v java >/dev/null 2>&1; then
  export JAVA_HOME="$(dirname "$(dirname "$(command -v java)")")"
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
  export ANDROID_HOME="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
fi
[[ -d "$ANDROID_HOME" ]] || export ANDROID_HOME="$HOME/Android/Sdk"

ADB="$ANDROID_HOME/platform-tools/adb"

echo "JAVA_HOME=${JAVA_HOME:-<unset>}"
echo "ANDROID_HOME=${ANDROID_HOME:-<unset>}"

FAILED_GATES=()
SKIPPED_GATES=()

run_gate() {
  local name="$1"; shift
  echo
  echo "=================================================================="
  echo "GATE: $name"
  echo "=================================================================="
  if "$@"; then
    echo "GATE PASS: $name"
  else
    echo "GATE FAIL: $name"
    FAILED_GATES+=("$name")
  fi
}

skip_gate() {
  echo "GATE SKIPPED: $1 — $2"
  SKIPPED_GATES+=("$1 ($2)")
}

gradle() { ./gradlew "$@" --no-daemon; }

# ---------------------------------------------------------------------------------------------
# Preconditions
# ---------------------------------------------------------------------------------------------
if [[ ! -x ./gradlew ]]; then
  echo "FATAL: ./gradlew not found or not executable. Run from a checkout of the repository."
  exit 2
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "FATAL: no usable JDK. Set JAVA_HOME."
  exit 2
fi

"$JAVA_HOME/bin/java" -version 2>&1 | tee "$REPORTS/tool-versions.txt"
./gradlew --version >> "$REPORTS/tool-versions.txt" 2>&1

# ---------------------------------------------------------------------------------------------
# Mandatory gates
# ---------------------------------------------------------------------------------------------
run_gate "clean"            gradle clean
run_gate "assembleDebug"    gradle assembleDebug
run_gate "assembleRelease"  gradle assembleRelease
run_gate "bundleRelease"    gradle bundleRelease
run_gate "unit tests"       gradle testDebugUnitTest
run_gate "lint (debug)"     gradle :app:lintDebug
run_gate "lint (release)"   gradle :app:lintRelease

# Coverage is a gate in its own right: the crypto engine going untested is a release blocker even
# when every test that does exist passes.
if ./gradlew tasks --all -q 2>/dev/null | grep -q "koverXmlReport"; then
  run_gate "coverage" gradle koverXmlReport koverHtmlReport
else
  skip_gate "coverage" "no Kover task in this build"
fi

# ---------------------------------------------------------------------------------------------
# Device gates
# ---------------------------------------------------------------------------------------------
DEVICE_COUNT=0
if [[ -x "$ADB" ]]; then
  DEVICE_COUNT="$("$ADB" devices | grep -cw "device" || true)"
fi

if [[ "$SKIP_DEVICE" == "1" ]]; then
  skip_gate "instrumented tests" "--no-device requested"
elif [[ "$DEVICE_COUNT" -lt 1 ]]; then
  skip_gate "instrumented tests" "NOT RUN — ENVIRONMENT UNAVAILABLE: no device or emulator attached"
else
  echo "Devices attached:"; "$ADB" devices -l
  run_gate "instrumented tests" gradle connectedDebugAndroidTest
fi

# ---------------------------------------------------------------------------------------------
# Collect reports
# ---------------------------------------------------------------------------------------------
echo
echo "Collecting reports into $REPORTS ..."
find . -path '*/build/test-results/*' -name '*.xml' -exec cp {} "$REPORTS/junit/" \; 2>/dev/null || true
find . -path '*/build/reports/lint-results*' -name '*.xml' -exec cp {} "$REPORTS/lint/" \; 2>/dev/null || true
find . -path '*/build/reports/kover/*' -name '*.xml' -exec cp {} "$REPORTS/coverage/" \; 2>/dev/null || true
find . -path '*androidTest-results*' -name '*.xml' -exec cp {} "$REPORTS/junit/" \; 2>/dev/null || true

# ---------------------------------------------------------------------------------------------
# Verdict
# ---------------------------------------------------------------------------------------------
echo
echo "=================================================================="
if [[ ${#SKIPPED_GATES[@]} -gt 0 ]]; then
  echo "SKIPPED GATES (these are NOT passes):"
  printf '  - %s\n' "${SKIPPED_GATES[@]}"
fi

if [[ ${#FAILED_GATES[@]} -gt 0 ]]; then
  echo "FAILED GATES:"
  printf '  - %s\n' "${FAILED_GATES[@]}"
  echo "RESULT: BLOCKED"
  exit 1
fi

if [[ ${#SKIPPED_GATES[@]} -gt 0 ]]; then
  echo "RESULT: CONDITIONAL — every gate that ran passed, but some did not run."
  exit 0
fi

echo "RESULT: all mandatory gates passed."
exit 0
