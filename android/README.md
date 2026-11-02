# RODVIKS — Android-клиент мессенджера

Android-клиент (Kotlin + Jetpack Compose) для RODVIKS-сервера (REST + Socket.IO).

## Сборка

```bat
cd C:\rodviks\android
set ANDROID_HOME=C:\Users\<user>\AppData\Local\Android\Sdk
gradlew.bat assembleDebug
```

Требования: JDK 17+ (Android Studio JBR: `C:\Program Files\Android\Android Studio\jbr`),
Gradle 8.11.1 (wrapper), AGP 8.7.3, Kotlin 2.0.21.

## Стек

- Compose + Material3 (тёмная тема по умолчанию, светлая в настройках)
- Navigation Compose 2.8.5
- Retrofit 2.11.0 + Gson, OkHttp 4.12.0
- Socket.IO client 2.1.1 (EIO=4)
- Room 2.6.1 (кэш чатов/сообщений, kapt)
- DataStore 1.1.1 (токен, адрес сервера)
- Coil 2.7.0 (аватарки/картинки)
- Coroutines 1.9.0

## Архитектура

MVVM + ServiceLocator (без Hilt):

- `data/api` — Retrofit-интерфейс, DTO, interceptor (Bearer + адрес сервера)
- `data/socket` — Socket.IO-менеджер (events -> SharedFlow)
- `data/local` — Room (users, chats, messages)
- `data/session` — DataStore (token, userId, baseUrl)
- `data/repo` — AuthRepository / UserRepository / ChatRepository
- `viewmodel` — Login/Register/Chats/Chat/Search/Profile/Settings VM
- `ui/screens` — Compose-экраны, `ui/nav` — NavHost
- `service` — SocketService (уведомления о сообщениях в фоне)

## Адрес сервера

По умолчанию `http://10.0.2.2:3000` (эмулятор -> localhost ПК).
Меняется в Настройках (DataStore, применяется к OkHttp и Socket.IO).

## Уведомления

Локальный NotificationChannel `rodviks_messages`; SocketService
(LifecycleService) слушает сокет и показывает уведомления для входящих
сообщений. POST_NOTIFICATIONS запрашивается на Android 13+.
