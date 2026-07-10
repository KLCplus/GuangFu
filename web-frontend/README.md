# Web Frontend

Vue 3 + Vite + TypeScript Web 端骨架，包含用户端、管理端路由，Axios API 层、Pinia 状态和 ECharts 示例图表。

```bash
npm install
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

访问 `http://localhost:5173`。Vite 会将 `/api` 和 `/openapi` 代理到 `backend/.env.local` 中的后端地址，默认是 `http://127.0.0.1:8080`。

也可以从项目根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-local.ps1 -FrontendOnly
```

生产构建：

```bash
npm run build
```
