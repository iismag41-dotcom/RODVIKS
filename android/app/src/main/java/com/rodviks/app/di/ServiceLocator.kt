package com.rodviks.app.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.rodviks.app.data.api.ApiService
import com.rodviks.app.data.api.AuthInterceptor
import com.rodviks.app.data.local.AppDatabase
import com.rodviks.app.data.repo.AuthRepository
import com.rodviks.app.data.repo.ChatRepository
import com.rodviks.app.data.repo.UserRepository
import com.rodviks.app.data.session.SessionManager
import com.rodviks.app.data.socket.ChatSocket
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ServiceLocator {

    @Volatile
    private var initialized = false

    lateinit var sessionManager: SessionManager
        private set
    lateinit var api: ApiService
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var socket: ChatSocket
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var userRepository: UserRepository
        private set
    lateinit var chatRepository: ChatRepository
        private set
    lateinit var gson: Gson
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext

            gson = GsonBuilder().create()
            sessionManager = SessionManager(appContext)
            db = AppDatabase.get(appContext)

            val okHttp = okhttp3.OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(sessionManager))
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://rodviks.onrender.com/")
                .client(okHttp)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()

            api = retrofit.create(ApiService::class.java)
            socket = ChatSocket(sessionManager)
            authRepository = AuthRepository(api, sessionManager)
            userRepository = UserRepository(api, db)
            chatRepository = ChatRepository(api, db, gson)
            initialized = true
        }
    }
}
