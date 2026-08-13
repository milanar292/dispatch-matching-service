function Refresh-DriverLocation($driverId, $lat, $lng) {
    $body = @{ latitude = $lat; longitude = $lng; timestamp = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss") } | ConvertTo-Json
    Invoke-RestMethod -Uri "http://localhost:8080/drivers/$driverId/location" -Method Post -Body $body -ContentType "application/json" | Out-Null
}

Write-Host "Refreshing driver locations..."
Refresh-DriverLocation "000ba88b-37ac-4f0b-b094-e9dd714d5eda" 13.91 78.91
Refresh-DriverLocation "27a6b87e-7315-453f-9a46-078e6615d934" 13.93 78.87
Refresh-DriverLocation "fdfd7e9b-7269-44c2-96f4-35bd4bcbaada" 13.88 78.94

$requestBody = @{ riderId = "rider-live-demo"; pickupLat = 13.90; pickupLng = 78.90; dropoffLat = 14.00; dropoffLng = 79.00 } | ConvertTo-Json
$request = Invoke-RestMethod -Uri "http://localhost:8080/requests" -Method Post -Body $requestBody -ContentType "application/json"
Write-Host "Request created: $($request.status)"

if ($request.status -ne "DRIVER_RESERVED") {
    Write-Host "No driver available - check Fleet panel for staleness, then rerun."
    exit
}

Start-Sleep -Milliseconds 500
$assignment = (Invoke-RestMethod -Uri "http://localhost:8080/assignments") | Where-Object { $_.requestId -eq $request.id } | Select-Object -First 1
Write-Host "Assignment RESERVED - visible in ledger now. Confirming in 3s..."
Start-Sleep -Seconds 3

$confirmed = Invoke-RestMethod -Uri "http://localhost:8080/assignments/$($assignment.id)/confirm" -Method Patch
Write-Host "CONFIRMED. Completing in 3s..."
Start-Sleep -Seconds 3

Invoke-RestMethod -Uri "http://localhost:8080/assignments/$($confirmed.id)/complete" -Method Patch
Write-Host "COMPLETED. Driver recycled back to AVAILABLE."
