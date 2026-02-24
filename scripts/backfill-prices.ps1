param(
    [string]$ApiBaseUrl = "http://localhost:8080",
    [string]$StartDate = "2015-01-01",
    [string]$EndDate = "2025-12-31",
    [int]$ChunkMonths = 6,
    [string[]]$Codes = @("005930","000660","035420","005380","035720","051910","068270","105560"),
    [string]$LogPath = ""
)

if ($Codes.Count -eq 1 -and $Codes[0] -match ",") {
    $Codes = $Codes[0].Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }
}

$Codes = $Codes | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne "" }

$body = @{
    codes = $Codes
    startDate = $StartDate
    endDate = $EndDate
    chunkMonths = $ChunkMonths
} | ConvertTo-Json -Depth 5

Write-Host "Backfill request"
Write-Host "API: $ApiBaseUrl"
Write-Host "Range: $StartDate ~ $EndDate"
Write-Host "Codes: $($Codes -join ', ')"
Write-Host "ChunkMonths: $ChunkMonths"

try {
    $res = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/api/stock/backfill" -ContentType "application/json" -Body $body
    Write-Host "Backfill completed."
    $json = $res | ConvertTo-Json -Depth 10
    $json

    if ([string]::IsNullOrWhiteSpace($LogPath)) {
        $ts = Get-Date -Format "yyyyMMdd-HHmmss"
        $LogPath = ".\logs\backfill-$ts.json"
    }
    $dir = Split-Path -Parent $LogPath
    if (-not [string]::IsNullOrWhiteSpace($dir) -and -not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    $json | Out-File -FilePath $LogPath -Encoding utf8
    Write-Host "Saved log: $LogPath"

    if ($res.failedCodeCount -gt 0) {
        Write-Warning "Some codes failed and were skipped. Check the saved log."
    }
} catch {
    Write-Error $_
    if ($_.Exception.Response -and $_.Exception.Response.GetResponseStream) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $reader.BaseStream.Position = 0
        $reader.DiscardBufferedData()
        $reader.ReadToEnd() | Write-Host
    }
    exit 1
}
