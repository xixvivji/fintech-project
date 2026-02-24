param(
    [string]$ApiBaseUrl = "http://localhost:8080",
    [string]$StartDate = "2015-01-01",
    [string]$EndDate = "2025-12-31",
    [int]$ChunkMonths = 6,
    [string[]]$Codes = @("005930","000660","035420","005380","035720","051910","068270","105560")
)

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
    $res | ConvertTo-Json -Depth 5
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
