# InfiniteChat Web

Vite + React + TypeScript frontend, covering sign-in and registration, realtime chat, contacts, groups, notifications and the profile page.

## Running locally

```bash
npm install
npm run dev
```

The app is served at `http://127.0.0.1:5173` by default. In development the Vite proxy forwards the traffic, so the browser only ever makes same-origin requests and no CORS configuration is needed:

| Browser request | Forwarded to |
| --- | --- |
| `/api/**` | `http://127.0.0.1:10010` (the gateway) |
| `/ws/netty` | `ws://127.0.0.1:9101` (the Netty WebSocket) |

The proxy targets can be overridden with `VITE_PROXY_GATEWAY` / `VITE_PROXY_NETTY`. In `.env.development`:

- `VITE_API_BASE` empty → relative paths are used and hit the proxy; set a full address to connect to it directly.
- `VITE_WS_BASE=/ws/netty` → ignore the LAN `nettyUri` the backend hands out and go through the proxy. Leave it unset in production so the address from the backend is used.

For the backend startup steps, see `README.md` in the repository root.

## Quality checks

```bash
npm run lint
npm test
npm run build
```

A full integration run needs the infrastructure and backend services listed in `README.md` to be up first, plus a dedicated test account:

```bash
INFINITECHAT_TEST_ACCOUNT=<account> \
INFINITECHAT_TEST_PASSWORD=<password> \
npm run test:integration
```

The integration script exercises sign-in, authorisation, contacts/groups/conversations/notifications, image upload and download, token refresh, WebSocket heartbeats and message receipts, and signs the test account out when it finishes.

## Data contract

The backend uses 64-bit snowflake IDs, which fall outside JavaScript's safe integer range. The frontend always stores entity IDs as strings and performs lossless parsing and serialisation at the HTTP and WebSocket boundaries. Never convert a user, conversation or message ID with `Number(...)`.
