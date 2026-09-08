# InfiniteChat Web

Vite + React + TypeScript 前端，覆盖登录注册、实时聊天、通讯录、群组、通知、红包与个人中心。

## 本地运行

```bash
npm install
npm run dev
```

默认访问 `http://localhost:5173`，并通过 `VITE_API_BASE` 连接网关（开发环境默认为 `http://localhost:10010`）。

## 质量验证

```bash
npm run lint
npm test
npm run build
```

完整联调需要先启动仓库根目录 README 中列出的基础设施与后端服务，再提供一个专用测试账号：

```bash
INFINITECHAT_TEST_ACCOUNT=<账号> \
INFINITECHAT_TEST_PASSWORD=<密码> \
npm run test:integration
```

联调脚本会验证登录、鉴权、联系人/群组/会话/通知/余额、图片上传下载、令牌刷新、WebSocket 心跳和消息回执，并在结束时退出测试账号。

## 数据契约

Go 后端使用 64 位雪花 ID。前端统一将实体 ID 保存为字符串，并在 HTTP 与 WebSocket 边界执行无损解析和序列化，禁止通过 `Number(...)` 转换用户、会话、消息或红包 ID。
