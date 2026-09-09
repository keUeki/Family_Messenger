# InfiniteChat 本地开发运行手册

这份文档说明如何在本机把 InfiniteChat 的后端与前端跑起来，并让前端在开发模式下正常连上后端。
文末列出了这次修复的所有问题，方便你核对改动。

---

## 一、前置条件

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | `pom.xml` 中 `java.version=17` |
| Node.js | ≥ 22.6 | 前端单元测试用到 `--experimental-strip-types` |
| Docker Desktop | — | 运行 MySQL / Redis / Kafka / Nacos / MinIO / Canal / pgvector |

---

## 二、启动基础设施（Docker）

先确认 8 个容器都在运行：

```bash
docker ps --format "{{.Names}}\t{{.Ports}}"
```

应当能看到（宿主机端口 → 容器端口）：

| 容器 | 宿主机端口 | 用途 |
| --- | --- | --- |
| `mysql` | **49152** → 3306 | 主库 `InfiniteChat` |
| `redis` | **59000** → 6379 | 令牌、验证码、会话热数据（**database 2**） |
| `kafka` | **9092** | 消息与通知总线 |
| `zookeeper` | 2181 | Kafka 依赖 |
| `nacos` | **18375** → 8848 | 服务注册与发现 |
| `minio` | 9000 / 9090 | 头像、图片对象存储 |
| `canal-server` | 11111 | 订阅 MySQL binlog，回填 Redis 热数据 |
| `pgvector` | 54328 → 5432 | AI 向量库 |

> 注意端口都是非默认值，直接用 `localhost:3306` / `localhost:6379` 是连不上的。

---

## 三、配置环境变量

各服务的 `application.yml` 用 `${...}` 引用了 7 个环境变量，**不设置服务会启动失败**：

```
DB_PASSWORD          MySQL root 密码
REDIS_PASSWORD       Redis 密码
PGVECTOR_PASSWORD    pgvector 密码
MAIL_USERNAME        发送验证码的邮箱
MAIL_PASSWORD        邮箱应用专用密码
DASHSCOPE_API_KEY    阿里云百炼（通义千问）API Key
BIGMODEL_API_KEY     智谱 API Key
```

这些值目前保存在 IntelliJ 的运行配置里（`.idea/workspace.xml` 的 `<env>` 节点）。
从 IDE 启动会自动带上；从命令行启动需要自己 `export`。

---

## 四、编译

```bash
cd InfiniteChat
./mvnw -o clean install -DskipTests
```

Reactor 应输出 **7 个模块**（父工程 + Common / UserService / AiService / GateWay / OfflineDataService / RealTimeService）全部 SUCCESS。

> 如果 clean 阶段报 `The process cannot access the file ... jar`，说明服务还在运行，先停掉再编译。

---

## 五、启动后端（5 个服务）

从 IDE 直接运行 5 个 `*Application` 主类即可。命令行方式：

```bash
cd InfiniteChat
java -jar UserService/target/UserService-0.0.1-SNAPSHOT.jar
java -jar RealTimeService/target/RealTimeService-0.0.1-SNAPSHOT.jar
java -jar OfflineDataService/target/OfflineDataService-0.0.1-SNAPSHOT.jar
java -jar AiService/target/AiService-0.0.1-SNAPSHOT.jar
java -jar GateWay/target/GateWay-0.0.1-SNAPSHOT.jar
```

启动后的端口分配：

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| GateWay | **10010** | 所有 HTTP API 的统一入口 |
| OfflineDataService | 8101 | 历史/离线消息 |
| RealTimeService | 8102 | Spring 端口（仅注册用） |
| RealTimeService (Netty) | **9101** | WebSocket 实际监听端口 |
| UserService | 8104 | 用户、通讯录、群聊 |
| AiService | 8105 | AI 对话 |

**RealTimeService 有两个端口**：8102 是 Spring Boot / Nacos 注册端口，9101 才是 Netty
WebSocket 端口。两者不能混用（详见第八节问题 3）。

自检：

```bash
netstat -ano | grep LISTENING | grep -E ":(8101|8102|8104|8105|9101|10010)\b"
```

---

## 六、启动前端

```bash
cd InfiniteChat/web
npm install        # 首次
npm run dev
```

访问 **http://127.0.0.1:5173**。

### 前端是怎么连上后端的

开发模式下 **不需要配置跨域**，因为 `vite.config.ts` 里配了两条代理，浏览器看到的全是同源请求：

| 浏览器请求 | Vite 转发到 | 说明 |
| --- | --- | --- |
| `/api/**` | `http://127.0.0.1:10010` | 网关 |
| `/ws/netty` | `ws://127.0.0.1:9101` | Netty，`ws: true` 走 WebSocket 升级 |

对应的 `web/.env.development`：

```
VITE_API_BASE=            # 留空 → axios 发相对路径 /api/**，命中代理
VITE_WS_BASE=/ws/netty    # 覆盖后端下发的 nettyUri，走代理
```

`VITE_WS_BASE` 的作用：登录接口返回的 `nettyUri` 是 Nacos 注册的**局域网 IP**
（例如 `ws://192.168.30.1:9101/ws/netty`），换个网络就会变、甚至不可达。开发时用这个变量强制走
本地代理即可。生产环境不设置它，前端就会使用后端下发的地址。

需要改代理目标时（不用改代码）：

```bash
VITE_PROXY_GATEWAY=http://127.0.0.1:10010 VITE_PROXY_NETTY=ws://127.0.0.1:9101 npm run dev
```

### 想绕过代理、直连网关

把 `.env.development` 改成 `VITE_API_BASE=http://127.0.0.1:10010` 也能工作：网关的 CORS 白名单
已经加了 `http://localhost:5173` 和 `http://127.0.0.1:5173`，鉴权过滤器也放行了 `OPTIONS` 预检。
不过仍建议用代理，配置最少。

---

## 七、验证（可选）

```bash
# 1. 登录，确认 nettyUri 带 ws:// 且端口是 9101
curl -s -X POST http://127.0.0.1:5173/api/user/login/password \
  -H "Content-Type: application/json" \
  -d '{"email":"<账号>","password":"<密码>"}'

# 2. 带上返回的 accessToken 访问业务接口
curl -s "http://127.0.0.1:5173/api/user/sessions?userId=<userId>" -H "Access-Token: <token>"

# 3. 前端自检
cd web && npm run lint && npm test && npm run build
```

浏览器里打开 DevTools → Network → WS，应该能看到 `ws://127.0.0.1:5173/ws/netty?accessToken=...`
握手返回 **101 Switching Protocols**，并且每 25 秒有一组 `ping` / `pong`。

---

## 八、这次修复的问题清单

### 后端

1. **UserService 的 Kafka 端口写错**
   `UserService/src/main/resources/application.yml` 里是 `localhost:19092`，而 Kafka 在 `9092`。
   结果每次发通知都要阻塞约 60 秒然后失败。已改为 `9092`（与其他两个服务一致）。

2. **浏览器无法建立 WebSocket 连接**
   前端把令牌放在 `?accessToken=` 查询参数里，而 `WebSocketAuthHeader` 只读 `Authorization`
   请求头 —— 浏览器的 WebSocket API 根本不能自定义请求头，所以网页端永远连不上。
   现在两种方式都支持（请求头额外兼容 `Bearer ` 前缀），并且在握手前把查询串从 URI 上摘掉，
   否则 `WebSocketServerProtocolHandler` 会因为路径不等于 `/ws/netty` 而拒绝握手。
   同时补上了原先只有一句 `// 记录日志` 注释、实际什么都没记的拒绝日志。

3. **登录返回的 `nettyUri` 端口错误、且缺少协议头**
   `NettyServiceLocator` 返回的是 Nacos 里的 Spring 端口 8102，而 Netty 监听的是 9101；
   而且返回值形如 `192.168.30.1:8102/ws/netty`，没有 `ws://`，前端 `new URL()` 解析不出来。
   现在 RealTimeService 把 Netty 端口作为 Nacos 元数据 `netty-port` 注册，
   `NettyServiceLocator` 读取该元数据并拼出完整的 `ws://host:9101/ws/netty`。

4. **聊天记录永远是空的（影响最大的一个）**
   `CanalClient` 的监听表集合是 `Set.of("infinitechat.message")`（全小写），
   但 binlog 里的库名是 `InfiniteChat`，`fullTableName` 为 `InfiniteChat.message`，
   `contains` 判断永远为 `false` —— 所有消息变更都被丢弃，Redis 热数据从未写入，
   于是 `/api/message/history` 和 7 天内的离线消息**始终返回空**。已改为忽略大小写比较。

5. **通知会被静默丢弃**
   `SystemNotificationConsumer` 只在 Redis 存在 `user:offline:` 标记时才做持久化，
   但该标记只在用户**断开连接**时写入。从未连接过的用户既没有 Channel 也没有标记，
   通知既不推送也不落库。现在只要没有可用 Channel 就一律转入持久化 topic。

6. **消息推送链路的空指针隐患**
   `ConsumerMessageService` 中 `sessionType` 为 null 会在拆箱时抛异常；
   `receiverId` 为 null 时 `receiverId.toString()` 会 NPE；
   并且只判断了 `channel != null` 而没判断 `isActive()`。三处都已加保护。

7. **网关没有 `/api/contact/**` 和 `/api/group/**` 的路由**
   通讯录和群聊接口在 UserService 上，但网关只配了 `/api/user`、`/api/message`、`/api/ai`，
   前端调用全部 404。已补上两条指向 `lb://UserService` 的路由。

8. **网关鉴权过滤器拦截 CORS 预检**
   `AuthorizeFilter` 是全局过滤器，`OPTIONS` 预检请求不带令牌，会被判为未登录返回 401，
   导致跨域直连时所有非简单请求失败。现在预检直接放行。

9. **AI 服务模型名不存在**
   `application.yml` 配的是 `qwen3.8-max`，百炼平台没有这个模型，
   调用一律返回 `InvalidParameter: url error`。已按参考项目改回 `qwen-max`。

10. **三个前端在调、后端却不存在的接口**（此前一律 404）
    - `GET /api/user/getUserInfo?userId=` —— 个人中心加载资料
    - `POST /api/user/updatePassword` —— 凭邮箱验证码修改密码
    - `GET /api/user/sessions?userId=` —— 聊天页左侧会话列表

    第三个是新增的 `SessionSummaryService`：单聊/AI 会话取对方的昵称头像，群聊取群名群头像，
    并带上每个会话的最后一条消息，按时间倒序。未读数固定返回 0，由前端结合离线消息计算
    （数据库里没有已读位点表）。

    > `updatePassword` 成功后会清掉该用户的登录令牌，需要重新登录 —— 这是有意的安全处理。

### 前端

11. **开发模式没有任何联调配置**
    `vite.config.ts` 没有 proxy、`VITE_API_BASE` 为空，浏览器请求 `/api/**` 全部打到
    Vite 自己身上（SPA fallback 还会返回 200，很有迷惑性）。已补上 `/api` 与 `/ws/netty` 两条代理。

12. **`buildWsUrl` 无法处理不带协议的地址**
    后端返回的 `host:port/path` 会被 `new URL(base, origin)` 当成相对路径，
    拼出 `http://127.0.0.1:5173/192.168.30.1:9101/ws/netty`，再交给 `new WebSocket()` 直接抛异常。
    现在支持 `ws://` / `wss://` / `http(s)://` / `//host` / `/path` / `host:port/path` 六种形态，
    并支持用 `VITE_WS_BASE` 覆盖。

13. **`npm test` 全部失败**
    测试直接 import `.ts` 源码，Node 22 需要 `--experimental-strip-types`；
    而且用到了 Vite 的 `@` 别名和 `import.meta.env`，Node 都不认识。
    新增 `test/alias-hooks.mjs` + `test/register-hooks.mjs` 两个加载钩子并更新了 `test` 脚本，
    现在 7 个用例全部通过。

---

## 九、常见问题

**登录 401 / 令牌很快失效**
访问令牌只有 30 分钟（`CommonConstant.ACCESS_TOKEN_EXPIRE_TIME`）。前端有自动刷新逻辑；
用 curl 手测时过期了重新登录即可。

**用 redis-cli 查不到数据**
业务数据在 **database 2**，记得加 `-n 2`：

```bash
docker exec redis redis-cli -a '<REDIS_PASSWORD>' --no-auth-warning -n 2 KEYS '*'
```

**收不到验证码邮件**
注册/改密需要 `sendCaptcha` 真发邮件。本地自测可以直接把验证码写进 Redis：

```bash
docker exec redis redis-cli -a '<REDIS_PASSWORD>' --no-auth-warning -n 2 SET '<邮箱>' '123456' EX 300
```

**聊天记录仍然为空**
确认 `canal-server` 容器在运行，且 OfflineDataService 日志里有
`收到变更: fullTableName=InfiniteChat.message` 之后**紧跟着** `======> binlog[...]` 一行。
只有前者没有后者，说明表名过滤又把事件丢掉了。

**日志里的中文是乱码**
服务日志是 GBK 编码的，用 `grep` 搜中文要么先 `iconv -f GBK -t UTF-8`，
要么直接搜 ASCII 片段（类名、`type: 103` 之类）。

**端口 5173 被占用**
Vite 会自动改用 5174，但 `.env.development` 和网关 CORS 白名单都是按 5173 写的。
先把占用进程杀掉，保证跑在 5173 上。

---

## 十、遗留事项（未处理）

- 数据库里仍有红包功能的四张表：`red_packet`、`red_packet_receive`、`user_balance`、`balance_log`。
  代码已经全部删干净，这几张表没人用，需要的话可以自行 `DROP`。
- 会话未读数没有已读位点表，`/api/user/sessions` 的 `count` 固定为 0，
  实际未读数由前端根据离线消息计算。
- `session` 表的 `avatar` 字段是这次联调时补加的（`ALTER TABLE session ADD COLUMN avatar VARCHAR(255) NULL`），
  建群功能依赖它。如果换一套数据库，记得同步这个字段。
