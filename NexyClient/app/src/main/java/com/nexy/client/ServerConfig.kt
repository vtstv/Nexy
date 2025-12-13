package com.nexy.client

/**
 * Configuration for connecting to Nexy server
 * 
 * Development Mode:
 * - Set SERVER_IP to your local network IP or localhost
 * - Use HTTP/WS (not HTTPS/WSS)
 * - Ensure cleartext traffic is enabled
 * 
 * Production Mode:
 * - Set SERVER_IP to production domain or IP
 * - Use HTTPS/WSS
 * - Remove cleartext traffic permission
 */
object ServerConfig {
    
    // Server Configuration (populated from BuildConfig based on flavor)
    val SERVER_IP: String = if (BuildConfig.DEBUG) {
        try {
            BuildConfig.SERVER_IP
        } catch (e: Exception) {
            "localhost"
        }
    } else {
        BuildConfig.SERVER_IP
    }
    
    val SERVER_PORT: String = if (BuildConfig.DEBUG) {
        try {
            BuildConfig.SERVER_PORT
        } catch (e: Exception) {
            "8080"
        }
    } else {
        BuildConfig.SERVER_PORT
    }
    
    val HTTP_PROTOCOL: String = if (BuildConfig.DEBUG) {
        try {
            BuildConfig.HTTP_PROTOCOL
        } catch (e: Exception) {
            "http"
        }
    } else {
        BuildConfig.HTTP_PROTOCOL
    }
    
    val WS_PROTOCOL: String = if (BuildConfig.DEBUG) {
        try {
            BuildConfig.WS_PROTOCOL
        } catch (e: Exception) {
            "ws"
        }
    } else {
        BuildConfig.WS_PROTOCOL
    }
    
    // API Configuration
    val SERVER_URL = "$HTTP_PROTOCOL://$SERVER_IP:$SERVER_PORT"
    val BASE_URL = "$SERVER_URL/api/"
    val WS_URL = "$WS_PROTOCOL://$SERVER_IP:$SERVER_PORT/ws"
    
    fun getFileUrl(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        if (path.startsWith("http")) return path
        // The server mounts files at /api/files, but the path returned by upload is /files/...
        // So we need to inject /api if it's missing
        if (path.startsWith("/files/")) {
            return "$SERVER_URL/api$path"
        }
        if (path.startsWith("/")) return "$SERVER_URL$path"
        return "$SERVER_URL/$path"
    }
    
    // For Android Emulator (use 10.0.2.2 instead of localhost)
    // const val SERVER_IP = "10.0.2.2"
    
    // WebSocket Configuration
    const val HEARTBEAT_INTERVAL_MS = 20_000L // 20 seconds
    const val RECONNECT_DELAY_MS = 5_000L // 5 seconds
    
    // API Timeouts (reduced for faster offline fallback)
    // Connect timeout is low to quickly detect unreachable server
    const val CONNECT_TIMEOUT_SECONDS = 5L
    const val READ_TIMEOUT_SECONDS = 15L
    const val WRITE_TIMEOUT_SECONDS = 15L
}
