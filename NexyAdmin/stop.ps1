# Nexy Admin Panel - Stop Script
# Run with: .\stop.ps1

Write-Host "🛑 Stopping Nexy Admin Panel..." -ForegroundColor Yellow

docker-compose down

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Services stopped successfully" -ForegroundColor Green
} else {
    Write-Host "❌ Failed to stop services" -ForegroundColor Red
}
