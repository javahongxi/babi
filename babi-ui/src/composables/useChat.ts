import { ref, reactive, readonly } from 'vue'
import type { ChatMessage, ModelInfo, UIConfig } from '../types/chat'
import { fetchUIConfig, deleteSession, deleteMemory, interruptChat } from '../api/client'

export function useChat() {
  const messages = ref<ChatMessage[]>([])
  const isStreaming = ref(false)
  const models = ref<ModelInfo[]>([])
  const selectedModel = ref<string>('')
  const badge = ref('Babi Agent')
  const sessionId = ref('default')
  const toolCallCount = ref(0)
  const streamingStarted = ref(false) // first token arrived?

  let abortController: AbortController | null = null

  async function loadConfig() {
    try {
      const config: UIConfig = await fetchUIConfig()
      if (config.badge) badge.value = config.badge
      if (config.models) models.value = config.models
      if (config.defaultModel) selectedModel.value = config.defaultModel
    } catch (e) {
      console.warn('Failed to load UI config:', e)
    }
  }

  async function sendMessage(text: string) {
    if (!text.trim() || isStreaming.value) return

    messages.value.push({ role: 'user', content: text.trim() })

    // Use reactive() so mutations on the proxy trigger Vue reactivity,
    // even though we hold a local reference and push it into messages.value.
    const agentMsg = reactive<ChatMessage>({
      role: 'agent',
      content: '',
      toolEvents: [],
      reasoning: '',
    })
    messages.value.push(agentMsg)

    isStreaming.value = true
    streamingStarted.value = false
    toolCallCount.value = 0
    abortController = new AbortController()

    const body: Record<string, string> = {
      message: text.trim(),
      sessionId: sessionId.value,
    }
    if (selectedModel.value) body.model = selectedModel.value

    try {
      const response = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal: abortController.signal,
      })

      if (!response.ok) throw new Error(`HTTP ${response.status}`)

      const reader = response.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop()!

        let event = ''
        let data = ''
        for (const line of lines) {
          if (line.startsWith('event:')) {
            event = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            data = line.slice(5).trim()
          } else if (line === '' && event) {
            handleStreamEvent(agentMsg, event, data)
            event = ''
            data = ''
          }
        }
      }
    } catch (err: any) {
      if (err.name === 'AbortError') {
        // user interrupted, keep partial content
      } else {
        if (!agentMsg.content) agentMsg.content = '⚠️ 连接中断，请重试'
        console.error('Stream error:', err)
      }
    } finally {
      isStreaming.value = false
      streamingStarted.value = false
      abortController = null
    }
  }

  function handleStreamEvent(msg: ChatMessage, event: string, data: string) {
    if (!data) return
    let parsed: any
    try { parsed = JSON.parse(data) } catch { return }

    switch (event) {
      case 'token':
        msg.content = (msg.content || '') + (parsed.data || '')
        if (!streamingStarted.value) streamingStarted.value = true
        break
      case 'reasoning':
        msg.reasoning = (msg.reasoning || '') + (parsed.data || '')
        if (!streamingStarted.value) streamingStarted.value = true
        break
      case 'tool_call':
        msg.toolEvents = [...(msg.toolEvents || []), { tool: parsed.tool || 'unknown', type: 'tool_call' }]
        toolCallCount.value++
        break
      case 'tool_result':
        msg.toolEvents = [...(msg.toolEvents || []), {
          tool: parsed.tool || 'unknown',
          type: 'tool_result',
          state: parsed.state,
        }]
        break
      case 'done':
        break
    }
  }

  async function interrupt() {
    abortController?.abort()
    await interruptChat(sessionId.value).catch(() => {})
  }

  async function clearChat() {
    messages.value = []
    toolCallCount.value = 0
    await deleteSession(sessionId.value)
  }

  async function clearMemory() {
    if (!confirm('确定要清除工作区记忆吗？这将删除 MEMORY.md，Agent 将不再记得之前的个人信息和上下文。')) return
    const message = await deleteMemory(sessionId.value)
    alert(message)
  }

  return {
    messages: readonly(messages),
    isStreaming: readonly(isStreaming),
    streamingStarted: readonly(streamingStarted),
    toolCallCount: readonly(toolCallCount),
    models,
    selectedModel,
    badge,
    sessionId,
    loadConfig,
    sendMessage,
    interrupt,
    clearChat,
    clearMemory,
  }
}
