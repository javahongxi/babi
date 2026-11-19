# 本地知识库使用指南

对话驱动的本地 RAG 知识库：把文档"加入知识库"后，随时用自然语言跨文档语义检索。基于 AgentScope `agentscope-extensions-rag-simple` 扩展构建，向量数据持久化在本机 JSON 文件中。

## 1. 对话式用法

```
把 ~/Documents/产品手册.pdf 加入知识库
知识库里现在有哪些文档？
根据知识库回答：退货流程是几步？
把产品手册从知识库里删掉
```

对应四个工具（系统提示词已注入路由规则，LLM 自动选择）：

| 工具 | 作用 |
|------|------|
| `knowledge_add(path)` | 入库：分块 → DashScope embedding → 本地持久化。支持 pdf / docx / txt / md / rst（老式 .doc 需先转 .docx）。重复添加同一文件会先清旧块再入库（刷新语义） |
| `knowledge_search(query)` | 语义检索，返回 top-K 相关文本块，带来源文件路径与相似度分数 |
| `knowledge_list()` | 列出已入库文档及各文档 chunk 数 |
| `knowledge_remove(source)` | 按来源路径删除整个文档的所有 chunk |

路由约定：**已入库资料**问 `knowledge_search`；**未入库的 workspace 文件**走 glob/read 工具；回答引用检索结果时附来源路径。

## 2. 配置

`application.yml`（均有默认值，可整段省略）：

```yaml
babi:
  agent:
    knowledge:
      enabled: true                    # 关闭则不注册知识库工具
      embedding-model: text-embedding-v4
      dimensions: 1024                 # 须与 embedding 模型输出维度一致
      top-k: 5                         # knowledge_search 默认返回块数
      store-dir: ~/.babi/knowledge     # 持久化目录
```

- embedding 复用 `babi.dashscope.api-key`（DASHSCOPE_API_KEY），**无需额外密钥**；未配置 key 时知识库自动不启用
- 修改 `dimensions` 或更换 embedding 模型后，旧向量不兼容，需清空 store.json 重新入库

## 3. 存储与隐私边界

- 向量库：`{store-dir}/store.json`，扁平结构（id/text/docId/chunkId/source/vector），写入走临时文件 + 原子替换，重启自动加载
- **文档本体不出本机**；入库与检索时分块文本会直连 DashScope embedding API（BYOK，与聊天同一隐私边界）
- 删除知识库全部数据：删掉 `~/.babi/knowledge/store.json` 即可

## 4. 实现要点

- `JsonFileVectorStore` 实现 rag-simple 的 `VDBStoreBase` SPI（add/search/delete），余弦相似度排序，替代无持久化的 InMemoryStore，且不引入外部向量数据库
- `SimpleKnowledge` 编排 embedding + 存储；检索不设分数阈值（避免误滤），仅取 top-K
- 依赖瘦身：pom 中排除 Milvus / Elasticsearch / PgVector / Qdrant 客户端与 Tika 解析器全家桶，仅保留 PDFBox / POI / DashScope SDK
- 注意：agentscope 2.0.4 中 core 的 `Knowledge`/`Document` 等 RAG 类型已标记 `@Deprecated(forRemoval)`（官方方向是应用层自集成），升级框架版本时需关注 rag-simple 的替代方案

## 5. 快速验证

```bash
echo "公司年假制度：入职满一年享 5 天年假，每多一年加 1 天，上限 15 天。" > ~/babi-workspace/demo/hr.txt
```

对话依次发送：

1. `把 demo/hr.txt 加入知识库` → 工具栏出现 knowledge_add，返回入库 chunk 数
2. `根据知识库回答：入职三年有几天年假？` → 走 knowledge_search，答出 7 天并引用来源
3. 重启后端再问一次 → 仍能检索到（持久化生效）

## 相关代码位置

| 关注点 | 文件 |
|--------|------|
| 持久化向量库 | `babi-agent/.../knowledge/JsonFileVectorStore.java` |
| 知识库工具 | `babi-agent/.../tool/KnowledgeTool.java` |
| 配置 | `babi-agent/.../config/KnowledgeProperties.java`、`application.yml` |
| 装配 | `babi-agent/.../config/AgentConfiguration.java`（harnessAgent Bean） |
| 提示词路由 | `babi-agent/.../prompt/BabiSystemPrompt.java`（knowledgeSection） |
| 单测 | `babi-agent/src/test/.../knowledge/JsonFileVectorStoreTest.java` |