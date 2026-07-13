# Web Frontend

Vue 3 + Vite + TypeScript Web 端骨架，包含用户端、管理端路由，Axios API 层、Pinia 状态和 ECharts 示例图表。

```bash
npm install
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

访问 `http://localhost:5173`。Vite 会将 `/api` 和 `/openapi` 代理到根目录 `.env` 中的后端地址，默认是 `http://127.0.0.1:8080`。

也可以从项目根目录运行：

```bash
./start-local.sh --frontend-only
```

生产构建：

```bash
npm run build
```
