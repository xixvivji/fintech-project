param(
    [string]$ContainerName = "fintech-postgres",
    [string]$DbName = "fintech",
    [string]$DbUser = "fintech",
    [Parameter(Mandatory = $true)]
    [string]$InputPath
)

$ErrorActionPreference = "Stop"

function Require-Command($name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $name"
    }
}

Require-Command "docker"

if (-not (Test-Path $InputPath)) {
    throw "Backup file not found: $InputPath"
}

$running = docker ps --filter "name=^$($ContainerName)$" --format "{{.Names}}"
if (-not ($running -contains $ContainerName)) {
    throw "Container '$ContainerName' is not running."
}

$fullInputPath = (Resolve-Path $InputPath).Path
$fileName = [System.IO.Path]::GetFileName($fullInputPath)
$unixTs = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$tmpPath = "/tmp/fintech-import-$unixTs-$fileName"

Write-Host "Restoring PostgreSQL database..."
Write-Host "Container: $ContainerName"
Write-Host "Database : $DbName"
Write-Host "User     : $DbUser"
Write-Host "Input    : $fullInputPath"

try {
    docker cp $fullInputPath "${ContainerName}:${tmpPath}"
    docker exec $ContainerName sh -lc "pg_restore -U $DbUser -d $DbName --clean --if-exists $tmpPath"
    Write-Host "Done. Restore completed."
}
finally {
    docker exec $ContainerName sh -lc "rm -f $tmpPath" | Out-Null
}
