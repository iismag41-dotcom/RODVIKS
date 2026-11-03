package com.rodviks.app.data.repo

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rodviks.app.data.api.ApiService
import com.rodviks.app.data.api.AuthRequest
import com.rodviks.app.data.api.Chat
import com.rodviks.app.data.api.CreateChatRequest
import com.rodviks.app.data.api.LoginBody
import com.rodviks.app.data.api.Message
import com.rodviks.app.data.api.PhoneLoginBody
import com.rodviks.app.data.api.PhoneRegisterBody
import com.rodviks.app.data.api.SetUsernameBody
import com.rodviks.app.data.api.UpdateUserRequest
import com.rodviks.app.data.api.User
import com.rodviks.app.data.local.AppDatabase
import com.rodviks.app.data.local.ChatEntity
import com.rodviks.app.data.local.MessageEntity
import com.rodviks.app.data.local.UserEntity
import com.rodviks.app.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import java.io.IOException

class ApiResult<out T>(val data: T? = null, val error: String? = null) {
    val isSuccess: Boolean get() = error == null && data != null
}

fun <T> success(data: T): ApiResult<T> = ApiResult(data = data, null)
fun <T> failure(error: String): ApiResult<T> = ApiResult(null, error)

fun Throwable.toUserMessage(): String = when (this) {
    is IOException -> "Ошибка сети. Проверьте соединение (${message ?: "нет деталей"})"
    is HttpException -> when (code()) {
        401 -> "Неверное имя пользователя или пароль"
        409 -> "Имя пользователя уже занято"
        400 -> "Некорректные данные"
        404 -> "Не найдено"
        else -> "Ошибка сервера (${code()})"
    }
    else -> "Что-то пошло не так"
}

class AuthRepository(
    private val api: ApiService,
    private val sessionManager: SessionManager
) {
    suspend fun login(username: String, password: String): ApiResult<User> {
        return try {
            val resp = api.login(LoginBody(username.trim().lowercase(), password))
            if (resp.isSuccessful && resp.body() != null) {
                val body = resp.body()!!
                sessionManager.saveAuth(body.token, body.user.id)
                success(body.user)
            } else {
                failure(httpMessage(resp.code()))
            }
        } catch (e: Exception) {
            failure(e.toUserMessage())
        }
    }

    suspend fun register(username: String, email: String, displayName: String?, password: String): ApiResult<User> {
        return try {
            val resp = api.register(
                AuthRequest(username.trim().lowercase(), password, displayName?.trim()?.ifBlank { null }, email.trim().lowercase())
            )
            if (resp.isSuccessful && resp.body() != null) {
                val body = resp.body()!!
                sessionManager.saveAuth(body.token, body.user.id)
                success(body.user)
            } else {
                failure(httpMessage(resp.code()))
            }
        } catch (e: Exception) {
            failure(e.toUserMessage())
        }
    }

    suspend fun logout() {
        sessionManager.clear()
    }

    private fun httpMessage(code: Int): String = when (code) {
        401 -> "Неверное имя пользователя или пароль"
        409 -> "Имя пользователя уже занято"
        400 -> "Некорректные данные"
        else -> "Ошибка сервера ($code)"
    }

    // ---- Phone + password auth ----

    /** Register with phone and password; on success saves the session. */
    suspend fun phoneRegister(phone: String, password: String): PhoneAuthResult {
        return try {
            val resp = api.phoneRegister(PhoneRegisterBody(phone, password))
            val body = resp.body()
            if (resp.isSuccessful && body != null) {
                sessionManager.saveAuth(body.token, body.user.id)
                PhoneAuthResult(user = body.user, needsUsername = body.needsUsername)
            } else {
                PhoneAuthResult(error = httpMessage(resp.code()))
            }
        } catch (e: Exception) {
            PhoneAuthResult(error = e.toUserMessage())
        }
    }

    /** Login with phone and password; on success saves the session. */
    suspend fun phoneLogin(phone: String, password: String): PhoneAuthResult {
        return try {
            val resp = api.phoneLogin(PhoneLoginBody(phone, password))
            val body = resp.body()
            if (resp.isSuccessful && body != null) {
                sessionManager.saveAuth(body.token, body.user.id)
                PhoneAuthResult(user = body.user, needsUsername = body.needsUsername)
            } else {
                PhoneAuthResult(error = httpMessage(resp.code()))
            }
        } catch (e: Exception) {
            PhoneAuthResult(error = e.toUserMessage())
        }
    }

    /** Sets the username after first phone login (requires saved session). */
    suspend fun setPhoneUsername(username: String, displayName: String?): ApiResult<User> {
        return try {
            val resp = api.setPhoneUsername(
                SetUsernameBody(username.trim().lowercase(), displayName?.trim()?.ifBlank { null })
            )
            if (resp.isSuccessful && resp.body() != null) {
                success(resp.body()!!)
            } else {
                failure(httpMessage(resp.code()))
            }
        } catch (e: Exception) {
            failure(e.toUserMessage())
        }
    }
}

data class PhoneAuthResult(
    val user: User? = null,
    val needsUsername: Boolean = false,
    val error: String? = null
)

class UserRepository(
    private val api: ApiService,
    private val db: AppDatabase
) {
    suspend fun search(query: String): ApiResult<List<User>> = try {
        val resp = api.searchUsers(query.trim())
        if (resp.isSuccessful && resp.body() != null) {
            val users = resp.body()!!
            db.userDao().upsertAll(users.map { it.toEntity() })
            success(users)
        } else failure("Ошибка сервера (${resp.code()})")
    } catch (e: Exception) {
        failure(e.toUserMessage())
    }

    suspend fun getUser(id: String): ApiResult<User> = try {
        val cached = db.userDao().get(id)
        val resp = api.getUser(id)
        if (resp.isSuccessful && resp.body() != null) {
            val user = resp.body()!!
            db.userDao().upsert(user.toEntity())
            success(user)
        } else if (cached != null) {
            success(cached.toModel())
        } else failure("Ошибка сервера (${resp.code()})")
    } catch (e: Exception) {
        val cached = db.userDao().get(id)
        if (cached != null) success(cached.toModel()) else failure(e.toUserMessage())
    }

    suspend fun getMe(): ApiResult<User> = try {
        val resp = api.getMe()
        if (resp.isSuccessful && resp.body() != null) {
            val user = resp.body()!!
            db.userDao().upsert(user.toEntity())
            success(user)
        } else failure("Ошибка сервера (${resp.code()})")
    } catch (e: Exception) {
        failure(e.toUserMessage())
    }

    suspend fun updateMe(displayName: String?, bio: String?, avatarUrl: String?): ApiResult<User> = try {
        val resp = api.updateMe(UpdateUserRequest(displayName, bio, avatarUrl))
        if (resp.isSuccessful && resp.body() != null) {
            val user = resp.body()!!
            db.userDao().upsert(user.toEntity())
            success(user)
        } else failure("Ошибка сервера (${resp.code()})")
    } catch (e: Exception) {
        failure(e.toUserMessage())
    }

    fun observeUser(id: String): Flow<UserEntity?> = db.userDao().observe(id)

    suspend fun setPresence(userId: String, online: Boolean) {
        db.userDao().get(userId)?.let {
            db.userDao().upsert(it.copy(online = online))
        }
    }
}

class ChatRepository(
    private val api: ApiService,
    private val db: AppDatabase,
    private val gson: Gson
) {
    fun observeChats(): Flow<List<ChatEntity>> = db.chatDao().observeAll()

    fun observeUser(id: String): Flow<UserEntity?> = db.userDao().observe(id)

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> = db.messageDao().observeChat(chatId)

    fun observeUnread(chatId: String, meId: String): Flow<Int> =
        db.messageDao().observeUnreadCount(chatId, meId)

    suspend fun syncChats(): ApiResult<Unit> = try {
        val resp = api.getChats()
        if (resp.isSuccessful && resp.body() != null) {
            val chats = resp.body()!!
            db.userDao().upsertAll(chats.mapNotNull { it.peer?.toEntity() })
            db.chatDao().upsertAll(chats.map { it.toEntity() })
            success(Unit)
        } else failure("Ошибка сервера (${resp.code()})")
    } catch (e: Exception) {
        failure(e.toUserMessage())
    }

    suspend fun getOrCreateChat(peerId: String): ApiResult<Chat> = try {
        val cached = db.chatDao().findByPeer(peerId)
        val resp = api.createChat(CreateChatRequest(peerId))
        if (resp.isSuccessful && resp.body() != null) {
            val chat = resp.body()!!
            chat.peer?.let { db.userDao().upsert(it.toEntity()) }
            db.chatDao().upsertAll(listOf(chat.toEntity()))
            success(chat)
        } else if (cached != null) {
            success(cached.toModel(db))
        } else failure("Не удалось открыть чат (${resp.code()})")
    } catch (e: Exception) {
        val cached = db.chatDao().findByPeer(peerId)
        if (cached != null) success(cached.toModel(db)) else failure(e.toUserMessage())
    }

    suspend fun loadMessages(chatId: String, before: String?): ApiResult<List<Message>> = try {
        val resp = api.getMessages(chatId, 50, before)
        if (resp.isSuccessful && resp.body() != null) {
            val messages = resp.body()!!
            db.messageDao().upsertAll(messages.map { it.toEntity(gson) })
            // remove pending local duplicates confirmed by server ids are distinct; pending rows keyed by clientId remain until ack
            success(messages)
        } else failure("Ошибка сервера (${resp.code()})")
    } catch (e: Exception) {
        failure(e.toUserMessage())
    }

    suspend fun insertLocalPending(entity: MessageEntity) {
        db.messageDao().upsert(entity)
    }

    suspend fun confirmSent(clientId: String, message: Message) {
        val pending = db.messageDao().findByClientId(clientId)
        if (pending != null) {
            db.messageDao().upsert(message.toEntity(gson).copy(clientId = clientId))
        } else {
            db.messageDao().upsert(message.toEntity(gson))
        }
    }

    suspend fun markFailed(clientId: String) {
        val pending = db.messageDao().findByClientId(clientId) ?: return
        db.messageDao().update(pending.copy(failed = true, pending = false))
    }

    suspend fun retryFailed(chatId: String): List<MessageEntity> = db.messageDao().getFailed(chatId)

    suspend fun removeLocal(clientId: String) {
        val m = db.messageDao().findByClientId(clientId) ?: return
        db.messageDao().upsert(m.copy(pending = false, failed = true))
    }

    suspend fun applyNewMessage(message: Message) {
        val msgEntity = message.toEntity(gson)
        val pendingWithClientId = message.clientId?.let { db.messageDao().findByClientId(it) }
        if (pendingWithClientId != null) {
            db.messageDao().upsert(msgEntity.copy(clientId = pendingWithClientId.clientId))
        } else {
            db.messageDao().upsert(msgEntity)
        }
        val chat = db.chatDao().get(message.chatId)
        if (chat != null) {
            db.chatDao().upsertAll(
                listOf(
                    chat.copy(
                        lastMessageId = message.id,
                        lastMessageText = message.text,
                        lastMessageImageUrl = message.imageUrl,
                        lastMessageSenderId = message.senderId,
                        lastMessageCreatedAt = message.createdAt,
                        updatedAt = message.createdAt
                    )
                )
            )
        }
    }

    suspend fun applyRead(chatId: String, userId: String, messageIds: List<String>) {
        for (id in messageIds) {
            val m = db.messageDao().get(id) ?: continue
            val reads = parseReads(m.readByJson).toMutableSet()
            if (reads.add(userId)) {
                db.messageDao().update(m.copy(readByJson = gson.toJson(reads)))
            }
        }
    }

    suspend fun markLocalRead(chatId: String, meId: String) {
        val messages = db.messageDao().getChat(chatId)
        for (m in messages) {
            if (m.senderId != meId) {
                val reads = parseReads(m.readByJson).toMutableSet()
                if (reads.add(meId)) {
                    db.messageDao().update(m.copy(readByJson = gson.toJson(reads)))
                }
            }
        }
    }

    suspend fun chatById(chatId: String): ChatEntity? = db.chatDao().get(chatId)

    suspend fun clearLocal() {
        db.chatDao().clearAll()
        db.messageDao().clearAll()
    }

    private fun parseReads(json: String): List<String> = try {
        gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

fun User.toEntity(): UserEntity = UserEntity(
    id = id, username = username, email = email, displayName = displayName,
    avatarUrl = avatarUrl, bio = bio, lastSeenAt = lastSeenAt, online = false
)

fun UserEntity.toModel(): User = User(
    id = id, username = username, email = email, displayName = displayName,
    avatarUrl = avatarUrl, bio = bio, lastSeenAt = lastSeenAt
)

fun Chat.toEntity(): ChatEntity = ChatEntity(
    id = id, type = type, createdAt = createdAt, updatedAt = updatedAt,
    peerId = peer?.id,
    lastMessageId = lastMessage?.id,
    lastMessageText = lastMessage?.text,
    lastMessageImageUrl = lastMessage?.imageUrl,
    lastMessageSenderId = lastMessage?.senderId,
    lastMessageCreatedAt = lastMessage?.createdAt
)

fun Message.toEntity(gson: Gson): MessageEntity = MessageEntity(
    id = id, chatId = chatId, senderId = senderId, text = text,
    imageUrl = imageUrl, createdAt = createdAt,
    readByJson = gson.toJson(readBy ?: emptyList<String>()),
    pending = false, failed = false, clientId = clientId
)

fun ChatEntity.toModel(db: AppDatabase): Chat {
    val peer = peerId?.let { runCatching { runBlocking { db.userDao().get(it) } }.getOrNull() }
    return Chat(
        id = id, type = type, createdAt = createdAt, updatedAt = updatedAt,
        peer = peer?.toModel(),
        lastMessage = lastMessageId?.let {
            Message(
                id = it, chatId = this.id, senderId = lastMessageSenderId ?: "",
                text = lastMessageText, imageUrl = lastMessageImageUrl,
                createdAt = lastMessageCreatedAt ?: ""
            )
        }
    )
}
