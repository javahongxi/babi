export interface ChatMessage {
  role: 'user' | 'agent'
  content: string
  toolEvents?: ToolEvent[]
  reasoning?: string
}

export interface ToolEvent {
  tool: string
  type: 'tool_call' | 'tool_result' | 'subagent'
  state?: 'SUCCESS' | 'ERROR' | 'INTERRUPTED' | 'UNKNOWN' | 'RUNNING'
  /** 子 agent 展示名（仅 subagent 事件） */
  agent?: string
  /** 子 agent 任务摘要（仅 subagent 事件） */
  task?: string
}

/** 后端 ToolNotificationMiddleware 路由为子 agent 事件的框架工具集，与 Java 侧 SUBAGENT_TOOLS 保持一致 */
export const SUBAGENT_TOOLS = new Set([
  'agent_spawn', 'agent_generate',
  'task', 'task_output', 'task_cancel', 'task_list',
])

export type StreamEvent =
  | { event: 'token'; data: { data: string } }
  | { event: 'reasoning'; data: { data: string } }
  | { event: 'tool_call'; data: { tool: string } }
  | { event: 'tool_result'; data: { tool: string; state: string } }
  | { event: 'subagent'; data: { tool: string; agent: string; task?: string } }
  | { event: 'done'; data: string }

export interface ModelInfo {
  name: string
  displayName: string
  description: string
}

export interface UIConfig {
  badge?: string
  defaultModel?: string
  models?: ModelInfo[]
}

export interface WorkspaceItem {
  name: string
  path: string
  isDir: boolean
}

export interface FileContent {
  content: string
  language: string
  size: number
  error?: string
}

export interface PinnedRepo {
  name: string
  url: string
  desc: string
  lang: string
  stars: string
  forks: string
}

export interface PinnedRepos {
  username: string
  repos: PinnedRepo[]
}
