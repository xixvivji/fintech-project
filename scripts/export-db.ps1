param(
    [string]$ContainerName = "fintech-postgres",
    [string]$DbName = "fintech",
    [string]$DbUser = "fintech",
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"

function Require-Command($name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $name"
    }
}

Require-Command "docker"

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $ts = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputPath = ".\backups\fintech-$ts.dump"
}

$outputDir = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDir) -and -not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

$running = docker ps --filter "name=^$($ContainerName)$" --format "{{.Names}}"
if (-not ($running -contains $ContainerName)) {
    throw "Container '$ContainerName' is not running."
}

$unixTs = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$tmpPath = "/tmp/fintech-export-$unixTs.dump"

Write-Host "Exporting PostgreSQL database..."
Write-Host "Container: $ContainerName"
Write-Host "Database : $DbName"
Write-Host "User     : $DbUser"
Write-Host "Output   : $OutputPath"

try {
    docker exec $ContainerName sh -lc "pg_dump -U $DbUser -d $DbName -Fc -f $tmpPath"
    docker cp "${ContainerName}:${tmpPath}" $OutputPath
    Write-Host "Done. Backup saved to: $OutputPath"
}
finally {
    docker exec $ContainerName sh -lc "rm -f $tmpPath" | Out-Null
}
