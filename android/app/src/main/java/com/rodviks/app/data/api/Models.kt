package com.rodviks.app.data.api

import com.google.gson.annotations.SerializedName

data class User(
    val id: String,
    val username: String,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    @SerializedName("lastSeenAt") val lastSeenAt: String? = null
)

data class AuthRequest(
    val username: String,
    val password: String,
    val displayName: String? = null,
    val email: String? = null
)

data class AuthResponse(
    val token: String,
    val user: User
)

data class UpdateUserRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null
)

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String? = null,
    val imageUrl: String? = null,
    val createdAt: String,
    val readBy: List<String> = emptyList(),
    val clientId: String? = null,
    // local-only fields (not sent to server)
    val pending: Boolean = false
)

data class SendMessageRequest(
    val text: String? = null,
    val imageUrl: String? = null,
    val clientId: String? = null
)

data class Chat(
    val id: String,
    val type: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val peer: User? = null,
    val lastMessage: Message? = null
)

data class CreateChatRequest(val peerId: String)

data class MediaUploadResponse(val url: String)

data class HealthResponse(val ok: Boolean)
