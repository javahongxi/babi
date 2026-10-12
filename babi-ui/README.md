# babi-ui

Babi Agent 的前端源码版聊天界面，用 Vue 3 + TypeScript + Vite 重写自 `babi-common` 内置的单文件 `index.html`。

内置页随各模块打包、零构建即用；本模块面向想改界面或学前端的读者，两条路径互不影响。

## 开发

```bash
npm install
npm run dev    # http://localhost:5173；/api 代理到本地 8901（babi-graph）
```

改源码即时热更新（HMR），无需重启后端或手动刷新。

## 构建

```bash
npm run build  # 产出到 dist/，不覆盖内置 static/index.html
```

技术栈、目录结构与取舍见根 `README.md` 的「babi-ui（前端源码版）」一节。
