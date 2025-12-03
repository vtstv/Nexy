# E2E Encryption Testing Script
# Testing E2E encryption in Nexy client

$env:PATH += ";C:\Users\vts\AppData\Local\Android\Sdk\platform-tools"

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "        NEXY E2E ENCRYPTION TEST SUITE                     " -ForegroundColor Cyan
Write-Host "============================================================`n" -ForegroundColor Cyan

Write-Host "Emulator 1 (emulator-5554):" -ForegroundColor Yellow
Write-Host "   - Variant: DEV DEBUG" -ForegroundColor White
Write-Host "   - E2E: DISABLED (plain text for debugging)" -ForegroundColor Red
Write-Host "   - Package: com.nexy.client.dev`n" -ForegroundColor White

Write-Host "Emulator 2 (127.0.0.1:5555):" -ForegroundColor Yellow
Write-Host "   - Variant: PROD RELEASE" -ForegroundColor White
Write-Host "   - E2E: ENABLED (AES-256-GCM)" -ForegroundColor Green
Write-Host "   - Package: com.nexy.client`n" -ForegroundColor White

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "TESTING INSTRUCTIONS:" -ForegroundColor Yellow
Write-Host "============================================================`n" -ForegroundColor Cyan

Write-Host "1. REGISTER USERS:" -ForegroundColor Cyan
Write-Host "   - Emulator 1: Register user_debug" -ForegroundColor White
Write-Host "   - Emulator 2: Register user_prod`n" -ForegroundColor White

Write-Host "2. CREATE CHAT:" -ForegroundColor Cyan
Write-Host "   - From one emulator, search for the other user" -ForegroundColor White
Write-Host "   - Create a private chat`n" -ForegroundColor White

Write-Host "3. SEND MESSAGES:" -ForegroundColor Cyan
Write-Host "   - Send test message from Emulator 1" -ForegroundColor White
Write-Host "   - Send test message from Emulator 2`n" -ForegroundColor White

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "LOG MONITORING COMMANDS:" -ForegroundColor Yellow
Write-Host "============================================================`n" -ForegroundColor Cyan

Write-Host "Debug Build Logs (emulator-5554):" -ForegroundColor Cyan
Write-Host 'adb -s emulator-5554 logcat -s "MessageOperations:D" "NexyApplication:D"' -ForegroundColor Gray
Write-Host ""

Write-Host "Production Build Logs (127.0.0.1:5555):" -ForegroundColor Green
Write-Host 'adb -s 127.0.0.1:5555 logcat -s "MessageOperations:D" "NexyApplication:D" "E2EManager:D"' -ForegroundColor Gray
Write-Host ""

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "DATABASE CHECK:" -ForegroundColor Yellow
Write-Host "============================================================`n" -ForegroundColor Cyan

Write-Host "PostgreSQL command:" -ForegroundColor Cyan
Write-Host 'docker exec -it messenger_postgres psql -U postgres -d nexy -c "SELECT message_id, sender_id, encrypted, LEFT(content, 80) FROM messages ORDER BY created_at DESC LIMIT 10;"' -ForegroundColor Gray
Write-Host ""

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "EXPECTED RESULTS:" -ForegroundColor Yellow
Write-Host "============================================================`n" -ForegroundColor Cyan

Write-Host "DEV DEBUG (Emulator 1):" -ForegroundColor Cyan
Write-Host "   - Log: 'Debug build - sending plain text message'" -ForegroundColor White
Write-Host "   - DB: encrypted = false, content = plain text`n" -ForegroundColor White

Write-Host "PROD RELEASE (Emulator 2):" -ForegroundColor Green
Write-Host "   - Log: 'Production build - initializing E2E encryption'" -ForegroundColor White
Write-Host "   - Log: 'Production build - encrypting message for user X'" -ForegroundColor White
Write-Host "   - Log: 'Message encrypted successfully'" -ForegroundColor White
Write-Host "   - DB: encrypted = true, content = JSON with ciphertext`n" -ForegroundColor White

Write-Host "============================================================`n" -ForegroundColor Cyan

$choice = Read-Host "Start log monitoring? (y/n)"
if ($choice -eq 'y') {
    Write-Host "`nStarting log monitoring for both emulators...`n" -ForegroundColor Yellow
    
    # Start log monitoring in separate windows
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:PATH += ';C:\Users\vts\AppData\Local\Android\Sdk\platform-tools'; Write-Host '=== DEV DEBUG LOGS ===' -ForegroundColor Cyan; adb -s emulator-5554 logcat -c; adb -s emulator-5554 logcat MessageOperations:D NexyApplication:D *:S"
    
    Start-Sleep -Seconds 1
    
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:PATH += ';C:\Users\vts\AppData\Local\Android\Sdk\platform-tools'; Write-Host '=== PROD RELEASE LOGS (E2E) ===' -ForegroundColor Green; adb -s 127.0.0.1:5555 logcat -c; adb -s 127.0.0.1:5555 logcat MessageOperations:D NexyApplication:D E2EManager:D E2ECryptoManager:D E2ESessionManager:D *:S"
    
    Write-Host "Log monitoring started in separate windows!" -ForegroundColor Green
}

Write-Host "`nScript completed. Happy testing!`n" -ForegroundColor Cyan
