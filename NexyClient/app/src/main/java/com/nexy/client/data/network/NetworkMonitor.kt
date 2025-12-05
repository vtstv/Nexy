package com.nexy.client.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkStatus {
    AVAILABLE,
    UNAVAILABLE,
    LOSING,
    LOST
}

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _networkStatus = MutableStateFlow(getCurrentNetworkStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus
    
    private val _isConnected = MutableStateFlow(isCurrentlyConnected())
    val isConnected: StateFlow<Boolean> = _isConnected
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val listeners = mutableSetOf<NetworkStatusListener>()
    
    interface NetworkStatusListener {
        fun onNetworkAvailable()
        fun onNetworkLost()
    }
    
    fun addListener(listener: NetworkStatusListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: NetworkStatusListener) {
        listeners.remove(listener)
    }
    
    fun startMonitoring() {
        if (networkCallback != null) return
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                _networkStatus.value = NetworkStatus.AVAILABLE
                _isConnected.value = true
                listeners.forEach { it.onNetworkAvailable() }
            }
            
            override fun onLosing(network: Network, maxMsToLive: Int) {
                Log.d(TAG, "Network losing: $maxMsToLive ms")
                _networkStatus.value = NetworkStatus.LOSING
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                _networkStatus.value = NetworkStatus.LOST
                _isConnected.value = false
                listeners.forEach { it.onNetworkLost() }
            }
            
            override fun onUnavailable() {
                Log.d(TAG, "Network unavailable")
                _networkStatus.value = NetworkStatus.UNAVAILABLE
                _isConnected.value = false
            }
            
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Log.d(TAG, "Network capabilities changed: internet=$hasInternet, validated=$validated")
                
                if (hasInternet && validated) {
                    _networkStatus.value = NetworkStatus.AVAILABLE
                    _isConnected.value = true
                }
            }
        }
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
            Log.d(TAG, "Network monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }
    
    fun stopMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
                Log.d(TAG, "Network monitoring stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister network callback", e)
            }
        }
        networkCallback = null
    }
    
    fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    private fun getCurrentNetworkStatus(): NetworkStatus {
        return if (isCurrentlyConnected()) NetworkStatus.AVAILABLE else NetworkStatus.UNAVAILABLE
    }
    
    fun observeConnectivity(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                trySend(false)
            }
            
            override fun onUnavailable() {
                trySend(false)
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, callback)
        
        trySend(isCurrentlyConnected())
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
