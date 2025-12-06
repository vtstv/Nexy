# Build Admin Panel Locally (without Docker)
# Run with: .\build.ps1

Write-Host "🔨 Building Nexy Admin Panel..." -ForegroundColor Cyan

# Check if Go is installed
try {
    $goVersion = go version
    Write-Host "✓ Go installed: $goVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Go is not installed. Please install Go 1.24+" -ForegroundColor Red
    exit 1
}

# Download dependencies
Write-Host "`n📦 Downloading dependencies..." -ForegroundColor Yellow
go mod download

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to download dependencies" -ForegroundColor Red
    exit 1
}

# Build executable
Write-Host "`n🏗️  Building executable..." -ForegroundColor Yellow
go build -o nexy-admin.exe ./cmd/admin

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✅ Build successful!" -ForegroundColor Green
    Write-Host "📦 Binary: nexy-admin.exe" -ForegroundColor White
    Write-Host "`n▶️  Run with: .\nexy-admin.exe" -ForegroundColor Cyan
} else {
    Write-Host "`n❌ Build failed" -ForegroundColor Red
}
