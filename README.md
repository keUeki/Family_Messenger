# InfiniteChat

An instant-messaging platform built as a Spring Cloud microservice stack with a
Vite + React + TypeScript frontend. It supports one-to-one and group chat over
WebSocket, contacts and friend requests, offline message backfill, and an
AI assistant with retrieval-augmented generation.

This document covers setting the project up and deploying it locally.

---

## Architecture

| Module | Responsibility | HTTP port |
| --- | --- | --- |
| `GateWay` | Single entry point for every HTTP API; routing and JWT authorisation | **10010** |
| `UserService` | Users, authentication, contacts, groups, conversation list | 8104 |
| `RealTimeService` | Netty WebSocket server; live message delivery and push | 8102 (+ **9101** for WebSocket) |
| `OfflineDataService` | Message history and offline backlog; consumes the MySQL binlog via Canal | 8101 |
| `AiService` | AI conversation, RAG knowledge base, tool calling | 8105 |
| `Common` | Shared DTOs, error codes, JWT and snowflake ID helpers | — (library) |
| `web` | Vite + React frontend | 5173 (dev server) |

Clients only ever talk to the gateway on **10010** and the Netty WebSocket on
**9101**. Services discover each other through Nacos.

---

## Prerequisites

| Dependency | Version | Why |
| --- | --- | --- |
| JDK | 17 | `java.version=17` in `pom.xml` |
| Node.js | >= 22.6 | The frontend tests need `--experimental-strip-types` |
| Docker Desktop | any current | Runs the eight infrastructure containers |
| Maven | bundled | Use the included `./mvnw` wrapper |

---

## 1. Start the infrastructure

Eight containers are required. There is no compose file in the repository, so
create them once with the images and host ports below — the ports are
deliberately non-default and the service configs expect exactly these values.

| Container | Image | Host port → container | Purpose |
| --- | --- | --- | --- |
| `mysql` | `mysql:8.0` | **49152** → 3306 | Primary database |
| `redis` | `redis:7` | **59000** → 6379 | Tokens, verification codes, hot conversation data (**database 2**) |
| `zookeeper` | `confluentinc/cp-zookeeper:7.4.0` | 2181 | Required by Kafka |
| `kafka` | `confluentinc/cp-kafka:7.4.0` | **9092** | Message and notification bus |
| `nacos` | `nacos/nacos-server:v2.1.0-slim` | **18375** → 8848 | Service registration and discovery |
| `minio` | `minio/minio:RELEASE.2025-02-18T16-25-55Z` | 9000 (API), 9090 (console) | Object storage for avatars and images |
| `canal-server` | `canal/canal-server:v1.1.7` | 11111 | Subscribes to the MySQL binlog, backfills the Redis hot cache |
| `pgvector` | `pgvector/pgvector:pg16` | 54328 → 5432 | Vector store for the AI knowledge base |

Confirm they are all running:

```bash
docker ps --format "{{.Names}}\t{{.Status}}\t{{.Ports}}"
```

> Because none of these use default ports, `localhost:3306` and `localhost:6379`
> will not connect. Use 49152 and 59000.

Two containers need a little configuration beyond starting them:

- **MySQL** must have binlog enabled in `ROW` format, and needs a `canal` user
  with replication privileges — `canal-server` connects as that user and its
  destination is named `example`.
- **Nacos** in standalone mode is enough; the services register under the
  `DEFAULT_GROUP` namespace.

---

## 2. Create the database schema

Apply `sql/schema.sql`. It creates the `InfiniteChat` database and all ten tables.

```bash
docker exec -i mysql mysql -uroot -p"$DB_PASSWORD" < sql/schema.sql
```

> **Warning:** the first statement is `DROP DATABASE IF EXISTS InfiniteChat`, which
> deletes every row along with it. Run it only when initialising a fresh environment.

Verify:

```bash
docker exec mysql mysql -uroot -p"$DB_PASSWORD" \
  -e "SELECT table_name FROM information_schema.tables WHERE table_schema='InfiniteChat';"
```

You should see ten tables: `user`, `user_balance`, `balance_log`, `session`,
`user_session`, `message`, `friend`, `apply_friend`, `red_packet`, `red_packet_receive`.

---

## 3. Create the MinIO bucket

Avatars and chat images are uploaded through presigned URLs into a bucket named
**`infinitechat`** (`CommonConstant.BUCKET_NAME`). Create it in the MinIO console
at http://localhost:9090, or with `mc`.

The browser uploads directly to MinIO, so its **CORS policy must allow the
frontend origin** (`http://127.0.0.1:5173`). Without this, image upload fails
even though everything else works.

---

## 4. Set the environment variables

Every service's `application.yml` references these seven values with `${...}`, and
**a service will fail to start if they are missing**:

| Variable | What it is |
| --- | --- |
| `DB_PASSWORD` | MySQL root password |
| `REDIS_PASSWORD` | Redis password |
| `PGVECTOR_PASSWORD` | pgvector password |
| `MAIL_USERNAME` | Mailbox that sends verification codes |
| `MAIL_PASSWORD` | App-specific password for that mailbox |
| `DASHSCOPE_API_KEY` | Alibaba Cloud Bailian (Tongyi Qianwen) API key |
| `BIGMODEL_API_KEY` | Zhipu API key |

Keep them in a local file that is never committed, and source it before starting
anything from a terminal:

```bash
# env.local.sh - add this filename to .gitignore
export DB_PASSWORD='...'
export REDIS_PASSWORD='...'
export PGVECTOR_PASSWORD='...'
export MAIL_USERNAME='...'
export MAIL_PASSWORD='...'
export DASHSCOPE_API_KEY='...'
export BIGMODEL_API_KEY='...'
```

```bash
source env.local.sh
```

If you run from IntelliJ instead, put them in each run configuration's
environment variables and the IDE supplies them automatically.

---

## 5. Build

```bash
./mvnw clean install -DskipTests
```

The reactor should report **7 modules** — the parent plus Common, UserService,
AiService, GateWay, OfflineDataService and RealTimeService — all SUCCESS.

> If the clean phase reports `The process cannot access the file ... jar`, a
> service is still running. Stop it and build again.

---

## 6. Start the backend

Start the five services. **Order matters**: `UserService` first, since the others
resolve it through Nacos, and `GateWay` last.

```bash
source env.local.sh
java -jar UserService/target/UserService-0.0.1-SNAPSHOT.jar
java -jar RealTimeService/target/RealTimeService-0.0.1-SNAPSHOT.jar
java -jar OfflineDataService/target/OfflineDataService-0.0.1-SNAPSHOT.jar
java -jar AiService/target/AiService-0.0.1-SNAPSHOT.jar
java -jar GateWay/target/GateWay-0.0.1-SNAPSHOT.jar
```

Each takes roughly 30-40 seconds to start; wait for `Started <Name>Application`.
Running the five `*Application` main classes from the IDE works equally well.

**RealTimeService binds two ports.** 8102 is the Spring Boot / Nacos registration
port; 9101 is where Netty actually accepts WebSocket connections. They are not
interchangeable — the port handed to clients comes from the Nacos metadata key
`netty-port`.

Check everything is listening:

```bash
netstat -ano | grep LISTENING | grep -E ":(8101|8102|8104|8105|9101|10010)\b"
```

---

## 7. Start the frontend

```bash
cd web
npm install        # first time only
npm run dev
```

Open **http://127.0.0.1:5173**.

### How the frontend reaches the backend

In development **no CORS configuration is needed**, because `vite.config.ts`
declares two proxies, so the browser only ever makes same-origin requests:

| Browser request | Proxied to | |
| --- | --- | --- |
| `/api/**` | `http://127.0.0.1:10010` | The gateway |
| `/ws/netty` | `ws://127.0.0.1:9101` | Netty, with `ws: true` for the upgrade |

The matching `web/.env.development`:

```
VITE_API_BASE=            # empty -> relative /api/** paths, which hit the proxy
VITE_WS_BASE=/ws/netty    # overrides the nettyUri from the backend, uses the proxy
```

`VITE_WS_BASE` matters because the `nettyUri` returned at login is the **LAN IP**
registered in Nacos (for example `ws://192.168.30.1:9101/ws/netty`), which changes
with the network and may not be reachable. Setting it forces the local proxy.
Leave it unset in production so the backend's address is used.

To point the proxies elsewhere without touching code:

```bash
VITE_PROXY_GATEWAY=http://127.0.0.1:10010 VITE_PROXY_NETTY=ws://127.0.0.1:9101 npm run dev
```

Connecting straight to the gateway also works — set
`VITE_API_BASE=http://127.0.0.1:10010`. The gateway's CORS allowlist already
includes `http://localhost:5173` and `http://127.0.0.1:5173`, and its
authorisation filter lets `OPTIONS` preflight requests through. The proxy is
still recommended, since it needs the least configuration.

---

## 8. Verify the setup

```bash
# The request path is reachable and the gateway's auth filter is active
curl -s "http://127.0.0.1:5173/api/user/sessions?userId=1"
# -> {"code":40100,"message":"Not logged in"}

# Log in; confirm nettyUri carries ws:// and port 9101
curl -s -X POST http://127.0.0.1:5173/api/user/login/password \
  -H "Content-Type: application/json" \
  -d '{"email":"<account>","password":"<password>"}'

# Call a business endpoint with the accessToken you got back
curl -s "http://127.0.0.1:5173/api/user/sessions?userId=<userId>" -H "Access-Token: <token>"
```

Frontend checks:

```bash
cd web && npm run lint && npm test && npm run build
```

In the browser, open DevTools → Network → WS. The
`ws://127.0.0.1:5173/ws/netty?accessToken=...` handshake should return
**101 Switching Protocols**, with a `ping` / `pong` pair every 25 seconds.

### Creating your first account

Registration sends a real verification code to the address you enter, so
`MAIL_USERNAME` / `MAIL_PASSWORD` must be working. To skip email while testing
locally, write the code straight into Redis and use the code-login tab:

```bash
docker exec redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning -n 2 \
  SET '<email>' '123456' EX 300
```

---

## Port reference

| Port | Service |
| --- | --- |
| 5173 | Vite dev server |
| **10010** | GateWay — all HTTP APIs |
| **9101** | RealTimeService — Netty WebSocket |
| 8101 / 8102 / 8104 / 8105 | OfflineDataService / RealTimeService (Spring) / UserService / AiService |
| 49152 | MySQL |
| 59000 | Redis (**database 2**) |
| 9092 / 2181 | Kafka / ZooKeeper |
| 18375 | Nacos |
| 9000 / 9090 | MinIO API / console |
| 11111 | Canal |
| 54328 | pgvector |

---

## Troubleshooting

**Login returns 401, or the token expires quickly**
Access tokens last 30 minutes (`CommonConstant.ACCESS_TOKEN_EXPIRE_TIME`). The
frontend refreshes them automatically; when testing with curl, log in again.

**`redis-cli` shows no data**
Application data lives in **database 2**, so pass `-n 2`:

```bash
docker exec redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning -n 2 KEYS '*'
```

**Chat history is empty**
Check that `canal-server` is running and that the OfflineDataService log shows
`Change received: fullTableName=InfiniteChat.message` **immediately followed by**
a `======> binlog[...]` line. The first without the second means the table-name
filter dropped the event. If Canal never connects, message history and the
offline backlog both stay empty, because they read the Redis hot cache that
Canal populates.

**Image upload fails but everything else works**
The browser uploads directly to MinIO, bypassing the gateway. Check the MinIO
bucket exists and its CORS policy allows `http://127.0.0.1:5173`.

**Port 5173 is already in use**
Vite falls back to 5174, but `.env.development` and the gateway CORS allowlist
are both written for 5173. Free the port instead.

**A service exits immediately on startup**
Almost always a missing environment variable — check that you sourced the env
file in the same shell.

---

## Notes on the current state

- **Unread counts are always 0.** There is no read-position table, so
  `/api/user/sessions` returns `count: 0` and the frontend derives the real
  figure from the offline backlog.
- **The AI knowledge base is seeded from files.** Everything under
  `AiService/src/main/resources/docs/*.md` is ingested into pgvector on every
  startup, and the vector table is recreated each time
  (`dropTableFirst(true)`). Editing those files changes what the assistant
  knows; knowledge added at runtime is appended back into them.
- **Changing a password invalidates that user's tokens** and forces a fresh
  login. This is deliberate.
