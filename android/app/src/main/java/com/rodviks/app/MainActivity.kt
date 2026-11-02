package com.rodviks.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.rodviks.app.data.session.SessionData
import com.rodviks.app.data.session.SessionManager
import com.rodviks.app.data.socket.SocketEvent
import com.rodviks.app.di.ServiceLocator
import com.rodviks.app.service.SocketService
import com.rodviks.app.ui.components.currentBaseUrlOverride
import com.rodviks.app.ui.nav.RodviksNavHost
import com.rodviks.app.ui.theme.RodviksTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val darkThemeFlow = MutableStateFlow(true)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Socket session data
        val sessionFlow: Flow<SessionData> = ServiceLocator.sessionManager.sessionFlow

        // keep image base url in sync
        lifecycleScope.launch {
            ServiceLocator.sessionManager.baseUrlFlow.collect { url ->
                currentBaseUrlOverride = url ?: SessionManager.DEFAULT_BASE_URL
            }
        }

        // apply socket events to the local DB (single source of truth)
        lifecycleScope.launch {
            ServiceLocator.socket.events.collect { event ->
                when (event) {
                    is SocketEvent.NewMessage -> ServiceLocator.chatRepository.applyNewMessage(event.message)
                    is SocketEvent.MessagesRead -> ServiceLocator.chatRepository.applyRead(
                        event.chatId, event.userId, event.messageIds
                    )
                    is SocketEvent.Presence -> ServiceLocator.userRepository.setPresence(
                        event.userId, event.online
                    )
                    else -> Unit
                }
            }
        }

        // start socket + notifications service when logged in
        lifecycleScope.launch {
            sessionFlow.collect { session ->
                if (session.token != null) {
                    ServiceLocator.socket.connect()
                    requestNotificationPermissionIfNeeded()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        SocketService.start(this@MainActivity)
                    }
                } else {
                    ServiceLocator.socket.disconnect()
                    SocketService.stop(this@MainActivity)
                }
            }
        }

        val initialChatId = intent?.getStringExtra("chatId")

        setContent {
            val darkTheme by darkThemeFlow.collectAsState()
            RodviksTheme(darkTheme = darkTheme) {
                RodviksNavHost(
                    darkThemeFlow = darkThemeFlow,
                    initialChatId = initialChatId
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val chatId = intent.getStringExtra("chatId")
        if (chatId != null) {
            // handled by nav host via a shared flow
            pendingChatNavigation.value = chatId
        }
    }

    companion object {
        val pendingChatNavigation = MutableStateFlow<String?>(null)
    }
}
