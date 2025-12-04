# verify_client_logs.ps1
Write-Host "Tailing Android Client logs for WebSocket events..."
adb logcat -s WSMessageHandler NexyWebSocket ChatViewModel
