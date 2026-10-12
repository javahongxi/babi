import type { UIConfig, WorkspaceItem, FileContent } from '../types/chat'

export async function fetchUIConfig(): Promise<UIConfig> {
  const res = await fetch('/api/ui/config')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function fetchFileTree(path: string): Promise<WorkspaceItem[]> {
  const res = await fetch(`/api/workspace/tree?path=${encodeURIComponent(path)}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function fetchFileContent(path: string): Promise<FileContent> {
  const res = await fetch(`/api/workspace/file?path=${encodeURIComponent(path)}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function deleteSession(sessionId: string): Promise<void> {
  await fetch(`/api/chat/session?sessionId=${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
  })
}

export async function deleteMemory(sessionId: string): Promise<string> {
  const res = await fetch(`/api/chat/memory?sessionId=${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
  })
  try {
    const data = await res.json()
    return data.message || 'Memory cleared'
  } catch {
    return res.ok ? 'Memory cleared' : `清除记忆失败 (HTTP ${res.status})`
  }
}

export async function interruptChat(sessionId: string): Promise<void> {
  await fetch(`/api/chat/interrupt?sessionId=${encodeURIComponent(sessionId)}`, {
    method: 'POST',
  })
}
