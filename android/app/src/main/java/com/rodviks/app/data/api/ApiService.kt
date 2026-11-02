package com.rodviks.app.data.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("auth/register")
    suspend fun register(@Body body: AuthRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginBody): Response<AuthResponse>

    @GET("users/me")
    suspend fun getMe(): Response<User>

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UpdateUserRequest): Response<User>

    @GET("users")
    suspend fun searchUsers(@Query("q") query: String): Response<List<User>>

    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): Response<User>

    @GET("chats")
    suspend fun getChats(): Response<List<Chat>>

    @POST("chats")
    suspend fun createChat(@Body body: CreateChatRequest): Response<Chat>

    @GET("chats/{id}/messages")
    suspend fun getMessages(
        @Path("id") chatId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null
    ): Response<List<Message>>

    @POST("chats/{id}/messages")
    suspend fun sendMessage(
        @Path("id") chatId: String,
        @Body body: SendMessageRequest
    ): Response<Message>

    @Multipart
    @POST("media/upload")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part
    ): Response<MediaUploadResponse>

    @GET("health")
    suspend fun health(): Response<HealthResponse>
}

data class LoginBody(
    val username: String,
    val password: String
)
