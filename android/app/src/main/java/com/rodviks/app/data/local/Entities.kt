package com.rodviks.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val lastSeenAt: String? = null,
    val online: Boolean = false
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val type: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val peerId: String? = null,
    val lastMessageId: String? = null,
    val lastMessageText: String? = null,
    val lastMessageImageUrl: String? = null,
    val lastMessageSenderId: String? = null,
    val lastMessageCreatedAt: String? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "chatId") val chatId: String,
    val senderId: String,
    val text: String? = null,
    val imageUrl: String? = null,
    val createdAt: String,
    val readByJson: String = "[]",
    val pending: Boolean = false,
    val failed: Boolean = false,
    val clientId: String? = null
)
