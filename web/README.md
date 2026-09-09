# InfiniteChat Web

Vite + React + TypeScript 前端，覆盖登录注册、实时聊天、通讯录、群组、通知与个人中心。

## 本地运行

```bash
npm install
npm run dev
```

默认访问 `http://127.0.0.1:5173`。开发模式下由 Vite 代理转发，浏览器看到的是同源请求，无需跨域配置：

| 浏览器请求 | 转发目标 |
| --- | --- |
| `/api/**` | `http://127.0.0.1:10010`（网关） |
| `/ws/netty` | `ws://127.0.0.1:9101`（Netty WebSocket） |

代理目标可用 `VITE_PROXY_GATEWAY` / `VITE_PROXY_NETTY` 覆盖。`.env.development` 中：

- `VITE_API_BASE` 留空 → 走相对路径，命中代理；填写完整地址则直连该地址。
- `VITE_WS_BASE=/ws/netty` → 忽略后端下发的局域网 `nettyUri`，走代理。生产环境不设置，使用后端下发地址。

后端服务的启动步骤见仓库根目录的 `helpme.md`。

## 质量验证

```bash
npm run lint
npm test
npm run build
```

完整联调需要先启动 `helpme.md` 中列出的基础设施与后端服务，再提供一个专用测试账号：

```bash
INFINITECHAT_TEST_ACCOUNT=<账号> \
INFINITECHAT_TEST_PASSWORD=<密码> \
npm run test:integration
```

联调脚本会验证登录、鉴权、联系人/群组/会话/通知、图片上传下载、令牌刷新、WebSocket 心跳和消息回执，并在结束时退出测试账号。

## 数据契约

后端使用 64 位雪花 ID，超出 JavaScript 安全整数范围。前端统一将实体 ID 保存为字符串，并在 HTTP 与 WebSocket 边界执行无损解析和序列化，禁止通过 `Number(...)` 转换用户、会话或消息 ID。
