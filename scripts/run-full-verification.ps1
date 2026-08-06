<#
.SYNOPSIS
    TrueVault full verification run (Windows).

.DESCRIPTION
    Mirrors scripts/run-full-verification.sh. Exits non-zero when any mandatory gate fails so it can
    be wired into CI unchanged. No machine-specific absolute paths: JAVA_HOME and ANDROID_HOME are
    discovered or taken from the environment, everything else is relative to the repository root.

.PARAMETER NoDevice
    Skip connected tests even when a device is attached.

.EXAMPLE
    .\scripts\run-full-verification.ps1
    .\scripts\run-full-verification.ps1 -NoDevice
#>
[CmdletBinding()]
param(
    [switch]$NoDevice
)

$ErrorActionPreference = 'Continue'

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

$Reports = 'qa-reports'
foreach ($sub in 'junit', 'lint', 'coverage', 'benchmarks', 'screenshots') {
    New-Item -ItemType Directory -Force -Path (Join-Path $Reports $sub) | Out-Null
}

# ------------------------------------------------------------------------------------------------
# Tool discovery
# ------------------------------------------------------------------------------------------------
if (-not $env:JAVA_HOME) {
    $candidates = @(
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        "$env:ProgramFiles\Android\Android Studio\jbr"
    )
    foreach ($c in $candidates) {
        if (Test-Path (Join-Path $c 'bin\java.exe')) { $env:JAVA_HOME = $c; break }
    }
}
if (-not $env:JAVA_HOME) {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java.Source) }
}

if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
}

$Adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'

Write-Host "JAVA_HOME=$($env:JAVA_HOME)"
Write-Host "ANDROID_HOME=$($env:ANDROID_HOME)"

$FailedGates = @()
$SkippedGates = @()

function Invoke-Gate {
    param([string]$Name, [string[]]$GradleArgs)

    Write-Host ''
    Write-Host '=================================================================='
    Write-Host "GATE: $Name"
    Write-Host '=================================================================='

    & .\gradlew.bat @GradleArgs --no-daemon
    if ($LASTEXITCODE -eq 0) {
        Write-Host "GATE PASS: $Name"
    } else {
        Write-Host "GATE FAIL: $Name"
        $script:FailedGates += $Name
    }
}

function Skip-Gate {
    param([string]$Name, [string]$Reason)
    Write-Host "GATE SKIPPED: $Name - $Reason"
    $script:SkippedGates += "$Name ($Reason)"
}

# ------------------------------------------------------------------------------------------------
# Preconditions
# ------------------------------------------------------------------------------------------------
if (-not (Test-Path '.\gradlew.bat')) {
    Write-Error 'FATAL: gradlew.bat not found. Run from a checkout of the repository.'
    exit 2
}
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Write-Error 'FATAL: no usable JDK. Set JAVA_HOME.'
    exit 2
}

& (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1 | Tee-Object -FilePath (Join-Path $Reports 'tool-versions.txt')
& .\gradlew.bat --version 2>&1 | Add-Content -Path (Join-Path $Reports 'tool-versions.txt')

# ------------------------------------------------------------------------------------------------
# Mandatory gates
# ------------------------------------------------------------------------------------------------
Invoke-Gate 'clean'           @('clean')
Invoke-Gate 'assembleDebug'   @('assembleDebug')
Invoke-Gate 'assembleRelease' @('assembleRelease')
Invoke-Gate 'bundleRelease'   @('bundleRelease')
Invoke-Gate 'unit tests'      @('testDebugUnitTest')
Invoke-Gate 'lint (debug)'    @(':app:lintDebug')
Invoke-Gate 'lint (release)'  @(':app:lintRelease')

# Coverage is a gate in its own right: the crypto engine going untested is a release blocker even
# when every test that does exist passes.
$taskList = & .\gradlew.bat tasks --all -q --no-daemon 2>$null
if ($taskList -match 'koverXmlReport') {
    Invoke-Gate 'coverage' @('koverXmlReport', 'koverHtmlReport')
} else {
    Skip-Gate 'coverage' 'no Kover task in this build'
}

# ------------------------------------------------------------------------------------------------
# Device gates
# ------------------------------------------------------------------------------------------------
$deviceCount = 0
if (Test-Path $Adb) {
    $deviceCount = (& $Adb devices | Select-String -Pattern '\bdevice$').Count
}

if ($NoDevice) {
    Skip-Gate 'instrumented tests' '-NoDevice requested'
} elseif ($deviceCount -lt 1) {
    Skip-Gate 'instrumented tests' 'NOT RUN - ENVIRONMENT UNAVAILABLE: no device or emulator attached'
} else {
    & $Adb devices -l
    Invoke-Gate 'instrumented tests' @('connectedDebugAndroidTest')
}

# ------------------------------------------------------------------------------------------------
# Collect reports
# ------------------------------------------------------------------------------------------------
Write-Host ''
Write-Host "Collecting reports into $Reports ..."
Get-ChildItem -Recurse -Filter '*.xml' -Path '.' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match 'build\\test-results\\' -or $_.FullName -match 'androidTest-results' } |
    ForEach-Object { Copy-Item $_.FullName (Join-Path $Reports 'junit') -Force -ErrorAction SilentlyContinue }
Get-ChildItem -Recurse -Filter 'lint-results*.xml' -Path '.' -ErrorAction SilentlyContinue |
    ForEach-Object { Copy-Item $_.FullName (Join-Path $Reports 'lint') -Force -ErrorAction SilentlyContinue }
Get-ChildItem -Recurse -Filter '*.xml' -Path '.' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match 'reports\\kover' } |
    ForEach-Object { Copy-Item $_.FullName (Join-Path $Reports 'coverage') -Force -ErrorAction SilentlyContinue }

# ------------------------------------------------------------------------------------------------
# Verdict
# ------------------------------------------------------------------------------------------------
Write-Host ''
Write-Host '=================================================================='
if ($SkippedGates.Count -gt 0) {
    Write-Host 'SKIPPED GATES (these are NOT passes):'
    $SkippedGates | ForEach-Object { Write-Host "  - $_" }
}

if ($FailedGates.Count -gt 0) {
    Write-Host 'FAILED GATES:'
    $FailedGates | ForEach-Object { Write-Host "  - $_" }
    Write-Host 'RESULT: BLOCKED'
    exit 1
}

if ($SkippedGates.Count -gt 0) {
    Write-Host 'RESULT: CONDITIONAL - every gate that ran passed, but some did not run.'
    exit 0
}

Write-Host 'RESULT: all mandatory gates passed.'
exit 0
