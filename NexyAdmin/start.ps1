# Nexy Admin Panel - Start Script
# Run with: .\start.ps1

Write-Host "🚀 Starting Nexy Admin Panel..." -ForegroundColor Cyan

# Check if .env exists
if (-Not (Test-Path ".env")) {
    Write-Host "⚠️  No .env file found. Copying from .env.example..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
    Write-Host "✓ Created .env file. Please configure it before starting." -ForegroundColor Green
    exit 1
}

# Check if Docker is running
try {
    docker ps | Out-Null
    Write-Host "✓ Docker is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

# Stop existing containers
Write-Host "`n🛑 Stopping existing containers..." -ForegroundColor Yellow
docker-compose down 2>&1 | Out-Null

# Start services
Write-Host "`n🏗️  Building and starting services..." -ForegroundColor Cyan
docker-compose up -d --build

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✅ Nexy Admin Panel is running!" -ForegroundColor Green
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
    Write-Host "📱 Admin Panel: http://localhost:3000" -ForegroundColor White
    Write-Host "👤 Username: admin" -ForegroundColor White
    Write-Host "🔑 Password: admin123" -ForegroundColor White
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
    Write-Host "`n📋 View logs: docker-compose logs -f nexy-admin" -ForegroundColor Gray
    Write-Host "🛑 Stop: docker-compose down" -ForegroundColor Gray
} else {
    Write-Host "`n❌ Failed to start services" -ForegroundColor Red
    Write-Host "Check logs with: docker-compose logs" -ForegroundColor Yellow
}
