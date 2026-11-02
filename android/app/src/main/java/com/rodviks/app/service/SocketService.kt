package com.rodviks.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.rodviks.app.MainActivity
import com.rodviks.app.R
import com.rodviks.app.data.socket.SocketEvent
import com.rodviks.app.di.ServiceLocator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SocketService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "rodviks_messages"
        const val NOTIFICATION_ID_BASE = 1000
        const val ACTION_START = "com.rodviks.app.action.START"
        const val ACTION_STOP = "com.rodviks.app.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, SocketService::class.java).apply {
                action = ACTION_START
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SocketService::class.java))
        }
    }

    private var socketJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return Service.START_NOT_STICKY
            }
            else -> startListening()
        }
        return Service.START_STICKY
    }

    private fun startListening() {
        if (socketJob != null) return
        val socket = ServiceLocator.socket
        if (!socket.isConnected) {
            socket.connect()
        }
        socketJob = lifecycleScope.launch {
            socket.events.collectLatest { event ->
                if (event is SocketEvent.NewMessage) {
                    if (event.message.senderId != currentUserId()) {
                        showNotification(event)
                    }
                }
            }
        }
    }

    private fun currentUserId(): String? =
        runCatching {
            kotlinx.coroutines.runBlocking {
                ServiceLocator.sessionManager.currentUserId()
            }
        }.getOrNull()

    private fun showNotification(event: SocketEvent.NewMessage) {
        val message = event.message
        val peerName = runCatching {
            kotlinx.coroutines.runBlocking {
                val chat = ServiceLocator.chatRepository.chatById(message.chatId)
                chat?.peerId?.let { ServiceLocator.db.userDao().get(it)?.displayName ?: ServiceLocator.db.userDao().get(it)?.username }
            }
        }.getOrNull() ?: "Новое сообщение"

        val body = message.text
            ?: (if (message.imageUrl != null) "🖼 Фото" else "")

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chatId", message.chatId)
        }
        val pending = PendingIntent.getActivity(
            this, message.chatId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(peerName)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_BASE + message.chatId.hashCode() % 1000, notification)
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_messages),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_messages_desc)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        socketJob?.cancel()
        socketJob = null
        super.onDestroy()
    }
}
