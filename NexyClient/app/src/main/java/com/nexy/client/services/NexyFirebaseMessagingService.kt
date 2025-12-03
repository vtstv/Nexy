package com.nexy.client.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nexy.client.MainActivity
import com.nexy.client.R
import com.nexy.client.data.local.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NexyFirebaseMessagingService : FirebaseMessagingService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private val PUSH_NOTIFICATIONS_KEY = booleanPreferencesKey("push_notifications_enabled")
    private val NOTIFICATION_SOUND_KEY = booleanPreferencesKey("notification_sound_enabled")
    private val NOTIFICATION_SOUND_URI_KEY = stringPreferencesKey("notification_sound_uri")
    private val NOTIFICATION_VIBRATION_KEY = booleanPreferencesKey("notification_vibration_enabled")

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        // Send token to server
        // sendRegistrationToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            
            scope.launch {
                if (shouldShowNotification()) {
                    sendNotification(it.title, it.body)
                }
            }
        }
    }
    
    private suspend fun shouldShowNotification(): Boolean {
        val preferences = applicationContext.settingsDataStore.data.first()
        return preferences[PUSH_NOTIFICATIONS_KEY] ?: true
    }

    private suspend fun sendNotification(title: String?, messageBody: String?) {
        val preferences = applicationContext.settingsDataStore.data.first()
        val soundEnabled = preferences[NOTIFICATION_SOUND_KEY] ?: true
        val soundUriString = preferences[NOTIFICATION_SOUND_URI_KEY]
        val vibrationEnabled = preferences[NOTIFICATION_VIBRATION_KEY] ?: true
        
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = getString(R.string.default_notification_channel_id)
        val soundUri = if (soundUriString != null) {
            Uri.parse(soundUriString)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title ?: getString(R.string.app_name))
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        if (soundEnabled) {
            notificationBuilder.setSound(soundUri)
        } else {
            notificationBuilder.setSound(null)
        }
        
        if (vibrationEnabled) {
            notificationBuilder.setVibrate(longArrayOf(0, 250, 250, 250))
        } else {
            notificationBuilder.setVibrate(longArrayOf(0))
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_HIGH
            )
            if (!soundEnabled) {
                channel.setSound(null, null)
            }
            channel.enableVibration(vibrationEnabled)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        private const val TAG = "NexyFirebaseMsgService"
    }
}
