<#
.SYNOPSIS
    Starts the local Android emulator (AVD "esiai") headless and waits until it is booted.

.DESCRIPTION
    The emulator on this machine must run WITHOUT a window: the windowed mode is
    killed by a graphics-overlay conflict (PresentMon / crashpad) and every boot
    then dies with "A snapshot operation for 'esiai' is pending".

    A killed emulator can also leave a headless qemu process behind. That zombie
    keeps the AVD lock, so the next start fails with the same pending-snapshot
    error. This script therefore clears stale processes and lock files first.

.EXAMPLE
    .\tools\start-emulator.ps1
    .\tools\start-emulator.ps1 -Avd esiai -Wait
#>
[CmdletBinding()]
param(
    [string]$Avd = 'esiai',
    [string]$Emulator = 'D:\dev\android-sdk\emulator\emulator.exe',
    [string]$AvdHome = "$env:USERPROFILE\.android\avd",
    [int]$TimeoutMinutes = 8,
    [switch]$Wait
)

$ErrorActionPreference = 'Continue'

Write-Host "==> Clearing stale emulator processes"
Get-Process -ErrorAction SilentlyContinue |
    Where-Object { $_.ProcessName -match 'qemu-system|emulator|netsimd' } |
    ForEach-Object {
        Write-Host "    stopping $($_.ProcessName) ($($_.Id))"
        Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    }
Start-Sleep -Seconds 3

$avdDir = Join-Path $AvdHome $Avd
foreach ($file in @('multiinstance.lock', 'snapshot.lock.lock', 'pid', 'read-snapshot.txt', 'bootcompleted.ini')) {
    $path = Join-Path $avdDir $file
    if (Test-Path $path) {
        Remove-Item $path -Force -ErrorAction SilentlyContinue
    }
}
$snapshots = Join-Path $avdDir 'snapshots'
if (Test-Path $snapshots) {
    Remove-Item $snapshots -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "==> Starting AVD '$Avd' (headless)"
$process = Start-Process $Emulator -PassThru -WindowStyle Hidden -ArgumentList @(
    '-avd', $Avd,
    '-no-window',
    '-no-snapshot-load',
    '-no-snapshot-save',
    '-no-boot-anim',
    '-no-audio'
) -RedirectStandardError "$env:TEMP\esiai-emulator.log"

Write-Host "    emulator pid: $($process.Id)   log: $env:TEMP\esiai-emulator.log"

if (-not $Wait) {
    return
}

Write-Host "==> Waiting for boot (timeout ${TimeoutMinutes}m)"
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    $state = (& adb devices 2>$null | Out-String)
    if ($state -match 'offline') { & adb reconnect offline 2>$null | Out-Null }
    $boot = ''
    try { $boot = (& adb -s emulator-5554 shell getprop sys.boot_completed 2>$null) } catch { $boot = '' }
    if ("$boot".Trim() -eq '1') {
        Write-Host '==> Booted.'
        & adb devices
        return
    }
}

Write-Warning "Emulator did not finish booting within $TimeoutMinutes minutes."
Write-Warning "Check $env:TEMP\esiai-emulator.log for the reason."
exit 1
