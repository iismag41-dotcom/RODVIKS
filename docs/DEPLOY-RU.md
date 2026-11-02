# Размещение сервера RODVIKS (Россия)

Проект полностью self-hosted: не зависит от зарубежных облачных сервисов, SDK не требует Google API-ключей. Вся инфраструктура — ваш сервер + PostgreSQL.

## Вариант A: VPS в РФ (Timeweb, Selectel, reg.ru, Aeza и др.)

1. **Требования**: Ubuntu 22.04+, 1 vCPU / 1 ГБ RAM достаточно для MVP, 10 ГБ диск.

2. **Установка Node.js 22** (официальный репозиторий или зеркало):
```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
# При недоступности nodesource.com — npm-зеркало Яндекс:
# npm config set registry https://registry.npmjs.yandex.team/  (или https://npm.hub.yandex.ru)
```

3. **PostgreSQL 16**:
```bash
sudo apt install -y postgresql-16
sudo -u postgres psql -c "CREATE USER rodviks WITH PASSWORD 'СЛОЖНЫЙ_ПАРОЛЬ';"
sudo -u postgres psql -c "CREATE DATABASE rodviks OWNER rodviks;"
```
БД слушает только localhost (по умолчанию) — наружу не открывать.

4. **Backend**:
```bash
git clone <ваш-репозиторий> rodviks && cd rodviks/backend
npm ci
cp .env.example .env   # задать JWT_SECRET (32+ байт), DATABASE_URL
npx prisma migrate deploy
npm run build
node dist/index.js
```

5. **systemd-сервис** (`/etc/systemd/system/rodviks.service`):
```ini
[Unit]
Description=RODVIKS backend
After=network.target postgresql.service

[Service]
WorkingDirectory=/opt/rodviks/backend
ExecStart=/usr/bin/node dist/index.js
Restart=always
User=rodviks
EnvironmentFile=/opt/rodviks/backend/.env

[Install]
WantedBy=multi-user.target
```
```bash
sudo systemctl enable --now rodviks
```

6. **HTTPS (Caddy — автоматические сертификаты, работает в РФ)**:
```
rodviks.вашдомен.рф {
    reverse_proxy 127.0.0.1:3000
}
```
Для Let's Encrypt нужен домен; сертификат выпустится и обновится автоматически.

7. **Брандмауэр**: открыть только 80/443. Порт 3000 и 5432 — закрыты наружу.

8. **Приложение**: в настройках указать `https://rodviks.вашдомен.рф`. Debug APK допускает cleartext HTTP, для HTTPS ограничений нет.

## Вариант B: Docker

```bash
echo "JWT_SECRET=$(openssl rand -hex 32)" > .env
docker compose up -d --build
```
В `docker-compose.yml` backend сам применяет миграции при старте. При недоступности Docker Hub — зеркала registry (например, `docker pull mirror.gcr.io/library/postgres:16-alpine` или аналогичные российские registry-прокси).

## Зависимости и доступность

| Что | Откуда | Заметки для РФ |
|---|---|---|
| npm-пакеты | registry.npmjs.org | при блокировках — зеркало Яндекса |
| Docker-образы | docker.io | зеркала: mirror.gcr.io, российские прокси |
| Gradle/Android SDK | dl.google.com, maven.google.com | как правило доступны; при блокировке — VPN/прокси один раз, дальше кэш |
| Let's Encrypt | letsencrypt.org | работает из РФ; альтернатива — сертификаты НУЦ Минцифры (для внутренних доменов) |

Все зависимости скачиваются один раз при сборке; работающий сервер после установки не обращается к зарубежным сервисам.

## Резервное копирование

```bash
pg_dump -U rodviks rodviks | gzip > backup_$(date +%F).sql.gz
tar czf uploads_$(date +%F).tar.gz uploads/
```
