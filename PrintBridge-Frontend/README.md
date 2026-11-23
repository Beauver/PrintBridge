# PrintBridge frontend

The Angular app of PrintBridge. See the [root README](../README.md) for what the app does and how to start everything, and [docs/architecture.md](../docs/architecture.md) for the folders and layering.

```sh
npm ci
npm start                               # dev server on :4200, proxies /api to the backend on :8080
npx ng test --no-watch                  # unit tests (Vitest)
npx ng test --no-watch --coverage       # with coverage in coverage/
npx prettier --check "src/**/*.{ts,html,css,scss}"
npm run build -- --configuration production
```

- Node.js 22.22.3+, 24.15+ or 26+ (what Angular 22 supports); CI uses 24.
- Docker image: `docker build -t printbridge-frontend .` (nginx; `BACKEND_URL` says where `/api` goes, `UPLOAD_MAX_SIZE_MB` how large an upload may be, default 100).
