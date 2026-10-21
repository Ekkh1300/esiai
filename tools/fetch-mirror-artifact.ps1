# Seeds a Maven artifact pair into third_party/local-maven from the Tencent
# central mirror (or another fallback). Used when a mirror serves transient
# 502s for specific cold paths; see settings.gradle.kts ("localSeed").
# Usage:
#   tools\fetch-mirror-artifact.ps1 -Group io.insert-koin -Artifact koin-core -Version 4.0.0
param(
    [Parameter(Mandatory)][string]$Group,
    [Parameter(Mandatory)][string]$Artifact,
    [Parameter(Mandatory)][string]$Version,
    # Classifier suffix ("" for the main artifact)
    [string]$Classifier = "",
    # Set $true to also fetch -sources
    [switch]$SkipSource = $false
)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$groupPath = $Group -replace '\.', '/'
$dest = Join-Path $PSScriptRoot ("..\third_party\local-maven\{0}\{1}\{2}" -f $groupPath, $Artifact, $Version)
New-Item -ItemType Directory -Force -Path $dest | Out-Null

function Fetch($url) {
    try {
        $head = Invoke-WebRequest -UseBasicParsing -Method Head -Uri $url -TimeoutSec 30
        return ($head.StatusCode -eq 200)
    } catch {
        return $false
    }
}

$suffix = if ($Classifier) { $Classifier } else { "" }
$base = "$Artifact-$Version$suffix"
$mirror = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public"

foreach ($file in @("$base.pom", "$base.jar", "$base.module")) {
    $url = "$mirror/$groupPath/$Artifact/$Version/$file"
    if (Fetch $url) {
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile (Join-Path $dest $file) -TimeoutSec 120
        Write-Host "seeded $file"
    } else {
        Write-Host "absent  $file (ok if the mirror relies on POM fallback)"
    }
}
