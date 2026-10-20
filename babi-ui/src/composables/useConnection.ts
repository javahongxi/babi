import { ref, computed } from 'vue'

// Lightweight connection monitor for the local backend.
// - Tracks browser network state via native online/offline events.
// - Probes a cheap endpoint (/api/ui/config) to detect the backend process
//   going down while the page stays open, and to confirm recovery.
// State is module-level so a single timer set is shared across all callers.

const PROBE_ENDPOINT = '/api/ui/config'
const PROBE_TIMEOUT = 8000          // abort a hung probe
const ONLINE_HEARTBEAT = 30000      // idle interval when everything is up
const RECONNECT_INTERVAL = 5000     // faster probing while offline

const isBrowserOnline = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
const isBackendUp = ref(true)

let timer: ReturnType<typeof setTimeout> | null = null
let monitoring = false

const isOffline = computed(() => !isBrowserOnline.value || !isBackendUp.value)

const message = computed(() => {
  if (!isBrowserOnline.value) return '网络连接已断开，请检查网络'
  if (!isBackendUp.value) return '无法连接到后端服务，正在重试…'
  return ''
})

async function probeBackend() {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), PROBE_TIMEOUT)
  try {
    const res = await fetch(PROBE_ENDPOINT, { signal: controller.signal, cache: 'no-store' })
    isBackendUp.value = res.ok
  } catch {
    // network error, timeout, or aborted → treat as unreachable
    isBackendUp.value = false
  } finally {
    clearTimeout(timeout)
    scheduleNext()
  }
}

function scheduleNext() {
  if (!monitoring) return
  if (timer) clearTimeout(timer)
  timer = setTimeout(probeBackend, isOffline.value ? RECONNECT_INTERVAL : ONLINE_HEARTBEAT)
}

function handleOnline() {
  isBrowserOnline.value = true
  // Probe immediately instead of waiting for the next tick.
  probeBackend()
}

function handleOffline() {
  isBrowserOnline.value = false
}

function init() {
  if (monitoring) return
  monitoring = true
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  probeBackend()
}

function stop() {
  monitoring = false
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
}

export function useConnection() {
  return { isOffline, message, init, stop }
}
