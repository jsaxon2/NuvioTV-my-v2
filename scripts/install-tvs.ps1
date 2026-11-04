<#
.SYNOPSIS
  Builds the release, installs it on each TV over adb, then compiles it fully ("speed").

.DESCRIPTION
  Sideloaded installs start almost uncompiled on some boxes (dexopt status "verify"), which
  makes the app noticeably laggier until Android catches up in the background. A full
  "speed" compile right after installing removes that. Each TV gets the APK matching its CPU,
  and the compiles run on all TVs at the same time.

.EXAMPLE
  .\scripts\install-tvs.ps1
.EXAMPLE
  .\scripts\install-tvs.ps1 -SkipBuild
.EXAMPLE
  .\scripts\install-tvs.ps1 -Devices 192.168.1.68:5555
.EXAMPLE
  .\scripts\install-tvs.ps1 -Devices "192.168.1.68:5555,192.168.1.194:5555"
#>
param(
    # Box R 4K Plus (living room), Shield (bedroom), Ally's Shield (tube)
    [string[]]$Devices = @("192.168.1.70:5555", "192.168.1.68:5555", "192.168.1.194:5555"),
    [string]$Package = "com.nuvio.tv.test.jack",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
# With -File, a comma list arrives as one string; split it into separate TVs.
$Devices = @($Devices | ForEach-Object { $_ -split "[,\s]+" } | Where-Object { $_ })
$root = Split-Path -Parent $PSScriptRoot
$apkDir = Join-Path $root "app\build\outputs\apk\full\release"

if (-not $SkipBuild) {
    Write-Host "Building release..." -ForegroundColor Cyan
    Push-Location $root
    try {
        & .\gradlew.bat :app:assembleFullRelease --console=plain -q
        if ($LASTEXITCODE -ne 0) { throw "Build failed." }
    } finally {
        Pop-Location
    }
}

function Get-ApkFor([string]$device) {
    $abis = "$(& adb -s $device shell getprop ro.product.cpu.abilist)".Trim().Split(",")
    if (-not $abis[0]) { throw "Could not read the CPU type from $device." }
    foreach ($abi in $abis) {
        $apk = Join-Path $apkDir "app-full-$abi-release.apk"
        if (Test-Path $apk) { return $apk }
    }
    $universal = Join-Path $apkDir "app-full-universal-release.apk"
    if (Test-Path $universal) { return $universal }
    throw "No APK in $apkDir matches $device ($($abis -join ', '))."
}

$installed = @()
foreach ($device in $Devices) {
    Write-Host "`n== $device" -ForegroundColor Cyan
    # adb reports problems on stderr; let them through as text so one TV dropping off
    # the network skips that TV instead of stopping the whole run.
    $ErrorActionPreference = "Continue"
    try {
        & adb connect $device 2>&1 | Out-Null
        $state = "$(& adb -s $device get-state 2>$null)".Trim()
        if ($state -ne "device") {
            Write-Host "   Not reachable - skipped. Is the TV on with network debugging enabled?" -ForegroundColor Yellow
            continue
        }
        $apk = Get-ApkFor $device
        Write-Host "   Installing $(Split-Path -Leaf $apk)..."
        $result = (& adb -s $device install -r $apk 2>&1 | Select-Object -Last 1)
        if ("$result" -notmatch "Success") {
            Write-Host "   Install failed: $result" -ForegroundColor Red
            continue
        }
        Write-Host "   Installed." -ForegroundColor Green
        $installed += $device
    } catch {
        Write-Host "   Skipped: $($_.Exception.Message)" -ForegroundColor Yellow
    } finally {
        $ErrorActionPreference = "Stop"
    }
}

if ($installed.Count -eq 0) {
    Write-Host "`nNothing installed." -ForegroundColor Yellow
    exit 1
}

Write-Host "`nCompiling the app fully on $($installed.Count) TV(s) - this can take a few minutes..." -ForegroundColor Cyan
$jobs = foreach ($device in $installed) {
    Start-Job -ArgumentList $device, $Package -ScriptBlock {
        param($device, $package)
        $out = (& adb -s $device shell cmd package compile -m speed -f $package 2>&1 | Select-Object -Last 1)
        "$device : $out"
    }
}
$jobs | Wait-Job | Receive-Job | ForEach-Object { Write-Host "   $_" }
$jobs | Remove-Job

Write-Host "`nDone." -ForegroundColor Green
