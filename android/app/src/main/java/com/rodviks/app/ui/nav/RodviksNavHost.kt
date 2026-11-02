package com.rodviks.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rodviks.app.MainActivity
import com.rodviks.app.di.ServiceLocator
import com.rodviks.app.ui.screens.ChatScreen
import com.rodviks.app.ui.screens.ChatsScreen
import com.rodviks.app.ui.screens.LoginScreen
import com.rodviks.app.ui.screens.ProfileScreen
import com.rodviks.app.ui.screens.RegisterScreen
import com.rodviks.app.ui.screens.SearchScreen
import com.rodviks.app.ui.screens.SettingsScreen
import com.rodviks.app.ui.screens.SplashScreen
import com.rodviks.app.viewmodel.ChatViewModel
import com.rodviks.app.viewmodel.LoginViewModel
import com.rodviks.app.viewmodel.ProfileViewModel
import com.rodviks.app.viewmodel.RegisterViewModel
import com.rodviks.app.viewmodel.SearchViewModel
import com.rodviks.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CHATS = "chats"
    const val CHAT = "chat/{chatId}"
    const val SEARCH = "search"
    const val PROFILE = "profile/{userId}"
    const val SETTINGS = "settings"

    fun chat(chatId: String) = "chat/$chatId"
    fun profile(userId: String) = "profile/$userId"
}

@Composable
fun RodviksNavHost(
    darkThemeFlow: MutableStateFlow<Boolean>,
    initialChatId: String? = null
) {
    val navController = rememberNavController()
    val session by ServiceLocator.sessionManager.sessionFlow.collectAsState(initial = null)

    val startDestination = Routes.SPLASH

    val chatViewModel: ChatViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()

    // Deep-link navigation from notifications
    LaunchedEffect(Unit) {
        MainActivity.pendingChatNavigation.collect { chatId ->
            if (chatId != null && session?.token != null) {
                navController.navigate(Routes.chat(chatId))
                MainActivity.pendingChatNavigation.value = null
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                sessionFlow = ServiceLocator.sessionManager.sessionFlow,
                onDecided = { loggedIn ->
                    if (loggedIn) {
                        navController.navigate(Routes.CHATS) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            val loginViewModel: LoginViewModel = viewModel()
            LoginScreen(
                viewModel = loginViewModel,
                onLoggedIn = {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onGoRegister = { navController.navigate(Routes.REGISTER) }
            )
        }

        composable(Routes.REGISTER) {
            val registerViewModel: RegisterViewModel = viewModel()
            RegisterScreen(
                viewModel = registerViewModel,
                onRegistered = {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onGoLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.CHATS) {
            val chatsViewModel: com.rodviks.app.viewmodel.ChatsViewModel = viewModel()
            ChatsScreen(
                viewModel = chatsViewModel,
                onOpenChat = { chatId ->
                    navController.navigate(Routes.chat(chatId))
                },
                onNewChat = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { entry ->
            val chatId = entry.arguments?.getString("chatId") ?: return@composable
            val chatScope = rememberCoroutineScope()
            ChatScreen(
                viewModel = chatViewModel,
                chatId = chatId,
                onBack = { navController.popBackStack() },
                onOpenProfile = { openedChatId ->
                    // open the peer profile: resolve via chat repository off the main thread
                    chatScope.launch {
                        val peerId = ServiceLocator.chatRepository.chatById(openedChatId)?.peerId
                        if (peerId != null) {
                            navController.navigate(Routes.profile(peerId))
                        }
                    }
                }
            )
        }

        composable(Routes.SEARCH) {
            val searchViewModel: SearchViewModel = viewModel()
            SearchScreen(
                viewModel = searchViewModel,
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId ->
                    navController.navigate(Routes.chat(chatId)) {
                        popUpTo(Routes.SEARCH)
                    }
                }
            )
        }

        composable(
            Routes.PROFILE,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { entry ->
            val userId = entry.arguments?.getString("userId") ?: return@composable
            val meId = session?.userId
            val profileScope = rememberCoroutineScope()
            LaunchedEffect(userId) { profileViewModel.loadUser(userId) }
            ProfileScreen(
                viewModel = profileViewModel,
                isMe = userId == meId,
                onBack = { navController.popBackStack() },
                onWriteMessage = { peerUserId ->
                    // create or open chat then navigate (off the main thread)
                    profileScope.launch {
                        val result = ServiceLocator.chatRepository.getOrCreateChat(peerUserId)
                        val chatId = result.data?.id
                        if (chatId != null) {
                            navController.navigate(Routes.chat(chatId))
                        }
                    }
                }
            )
        }

        composable(Routes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel()
            val dark by darkThemeFlow.collectAsState()
            SettingsScreen(
                viewModel = settingsViewModel,
                darkTheme = dark,
                onThemeChange = { darkThemeFlow.value = it },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
