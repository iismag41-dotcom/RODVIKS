# RODVIKS API

Base URL: `http://<host>:3000`

Аутентификация: заголовок `Authorization: Bearer <token>` для всех маршрутов, кроме `/auth/*` и `/health`.

## POST /auth/register
Body: `{ "username": "string (3-24, [a-zA-Z0-9_])", "email": "string (валидный email, уникальный)", "displayName": "string?", "password": "string (8-128)" }`
Ответ 201: `{ "token": "jwt", "user": { id, username, email, displayName, avatarUrl, bio } }`
Ошибки: 409 username или email занят, 400 валидация.

## POST /auth/login
Body: `{ "username": "...", "password": "..." }`
Ответ 200: `{ token, user }`. Ошибка 401.

## GET /users/me
Ответ 200: user.

## PATCH /users/me
Body: `{ displayName?, bio?, avatarUrl? }`
Ответ 200: user.

## GET /users?q=<подстрока>
Минимум 2 символа. Ответ 200: `[user]` (до 20, исключая себя).

## GET /users/:id
Ответ 200: user / 404.

## GET /chats
Ответ 200: `[{ id, type, createdAt, updatedAt, peer: { id, username, displayName, avatarUrl, lastSeenAt } | null, lastMessage: { id, text, imageUrl, senderId, createdAt } | null }]` — сортировка по updatedAt desc.

## POST /chats
Body: `{ "peerId": "uuid" }`
Ответ 200 (существующий) или 201 (создан): chat. Ошибки: 400 сам с собой, 404 нет пользователя.

## GET /chats/:id/messages?limit=50&before=ISO
Пагинация по createdAt lt. Автоматически помечает сообщения прочитанными. Ответ 200: `[message]` (старые → новые).

## POST /chats/:id/messages
Body: `{ text?, imageUrl? }` (хотя бы одно, text до 4000 символов)
Ответ 201: `{ id, chatId, senderId, text, imageUrl, createdAt, readBy: [] }`

## POST /media/upload
Multipart, поле `image`. Разрешены jpg/png/webp/gif, до 10 МБ (по умолчанию).
Ответ 201: `{ "url": "http://host/uploads/<uuid>.<ext>" }`

## Socket.IO

Подключение: `io(url, { auth: { token: <jwt> } })`

Клиент → сервер:
- `message:send` `{ chatId, text?, imageUrl?, clientId? }` + ack `({ ok, message } | { error })`
- `typing` `{ chatId, typing }`
- `messages:read` `(chatId)`
- `chat:join` `(chatId)`

Сервер → клиент:
- `message:new` `{ id, chatId, senderId, text, imageUrl, createdAt, readBy, clientId? }`
- `typing` `{ chatId, userId, typing }`
- `presence` `{ userId, online }`
- `messages:read` `{ chatId, userId, messageIds }`
- `messages:read` после вызова messages:read — уведомление отправителю, что сообщения прочитаны.
