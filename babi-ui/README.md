# babi-ui

Babi Agent 的唯一前端，用 Vue 3 + TypeScript + Vite 实现聊天界面，后端不再内置静态页面。

## 开发

```bash
npm install
npm run dev    # http://localhost:5173；/api 代理到本地 8900（babi-agent）
```

改源码即时热更新（HMR），无需重启后端或手动刷新。

## 构建

```bash
npm run build  # 产出到 dist/，可部署到任意静态服务器
```

技术栈、目录结构与取舍见根 `README.md` 的「babi-ui（唯一前端）」一节。
