export interface ChatMessage {
  role: 'user' | 'agent'
  content: string
  toolEvents?: ToolEvent[]
  reasoning?: string
}

export interface ToolEvent {
  tool: string
  type: 'tool_call' | 'tool_result'
  state?: 'SUCCESS' | 'ERROR'
}

export type StreamEvent =
  | { event: 'token'; data: { data: string } }
  | { event: 'reasoning'; data: { data: string } }
  | { event: 'tool_call'; data: { tool: string } }
  | { event: 'tool_result'; data: { tool: string; state: string } }
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
