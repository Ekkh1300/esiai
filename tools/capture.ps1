# Capture a screenshot from the emulator as a valid PNG.
#
# `adb exec-out screencap -p > file` corrupts the binary under PowerShell
# (the redirection re-encodes it as UTF-16), so pull the file instead.
#
#   .\tools\capture.ps1 -Name chat -Serial emulator-5554
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [string]$Serial = 'emulator-5554',
    [string]$OutDir = "$env:TEMP\opencode"
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$remote = "/sdcard/_capture.png"
$local = Join-Path $OutDir "$Name.png"

& adb -s $Serial shell screencap -p $remote | Out-Null
& adb -s $Serial pull $remote $local 2>&1 | Out-Null
& adb -s $Serial shell rm $remote 2>&1 | Out-Null

if (-not (Test-Path $local)) {
    throw "capture failed for $Name"
}
$header = [System.IO.File]::ReadAllBytes($local)[0..3]
if ($header[0] -ne 0x89 -or $header[1] -ne 0x50) {
    throw "not a PNG: $local"
}
Write-Host "captured $local ($([math]::Round((Get-Item $local).Length / 1KB)) KB)"
