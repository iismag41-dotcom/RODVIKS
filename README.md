# RODVIKS — Android-мессенджер

Современный нативный мессенджер (Kotlin + Jetpack Compose) с backend на Node.js.

## Структура

```
rodviks/
├── android/            # Android-приложение (Kotlin, Compose, Material 3)
├── backend/            # Node.js + TypeScript + Express + Prisma + Socket.IO
├── docs/               # Документация
├── docker-compose.yml  # PostgreSQL + backend (Docker)
└── README.md
```

## Требования

- Node.js 22+
- PostgreSQL 16 (через Docker ИЛИ локально)
- Android Studio (для сборки APK): JDK 17+, Android SDK 35

## Быстрый запуск (Docker)

```bash
# 1. Задай секрет
echo "JWT_SECRET=$(openssl rand -hex 32)" > .env

# 2. Подними PostgreSQL + backend
docker compose up -d --build

# 3. Проверь
curl http://localhost:3000/health   # {"ok":true}
```

## Запуск без Docker (локальная разработка)

### 1. PostgreSQL

**Вариант A — Docker только для БД:**
```bash
docker compose up -d db
```

**Вариант B — локальная PostgreSQL (Windows, портативная, в этом репозитории):**
```bat
rodviks\start-db.bat
```

### 2. Backend

```bash
cd backend
cp .env.example .env        # отредактируй при необходимости
npm install
npx prisma migrate dev --name init
npm run dev
```

Сервер: http://localhost:3000

### 3. Android

1. Открой папку `android/` в Android Studio.
2. Для эмулятора сервер уже доступен: приложение использует `http://10.0.2.2:3000`.
   Для реального телефона в настройках приложения укажи адрес компьютера
   в той же Wi-Fi сети: `http://<IP-компьютера>:3000`.
3. Собери APK: `gradlew.bat assembleDebug` (или Build > Build APK в Android Studio).
4. APK появится в `android/app/build/outputs/apk/debug/app-debug.apk`.

## API

| Метод | Путь | Описание |
|---|---|---|
| POST | /auth/register | Регистрация |
| POST | /auth/login | Вход |
| GET | /users/me | Мой профиль |
| PATCH | /users/me | Редактирование профиля |
| GET | /users?q= | Поиск пользователей |
| GET | /users/:id | Профиль пользователя |
| GET | /chats | Список чатов |
| POST | /chats | Создать/найти личный чат |
| GET | /chats/:id | Информация о чате |
| GET | /chats/:id/messages | История сообщений |
| POST | /chats/:id/messages | Отправить сообщение |
| POST | /media/upload | Загрузка изображения |

Socket.IO: `message:send`, `typing`, `messages:read`, `presence`, `message:new`.

## Документация

- `docs/API.md` — полное описание REST API и Socket.IO-событий.
- `docs/SECURITY.md` — безопасность: что реализовано, известные ограничения MVP, рекомендации для продакшена.
- `docs/DEPLOY-RU.md` — размещение сервера в России (VPS, systemd, Docker, зеркала, HTTPS, бэкапы).
- `START-RU.md` — быстрый локальный запуск (Windows, без Docker).

## Безопасность

- Пароли — Argon2.
- JWT-аутентификация.
- Проверка членства в чате на каждом запросе.
- Zod-валидация входных данных.
- Rate limiting.
- Только изображения при загрузке, лимит размера.
- Секреты в `.env` (`.env` в `.gitignore`).
