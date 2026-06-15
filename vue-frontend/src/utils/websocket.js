const WS_URL = `ws://${window.location.hostname}:8080/ws/dashboard`

class DashboardWebSocket {
  constructor() {
    this.ws = null
    this.listeners = new Map()
    this.reconnectTimer = null
  }

  connect() {
    this.ws = new WebSocket(WS_URL)

    this.ws.onopen = () => {
      console.log('[WS] 连接已建立')
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer)
        this.reconnectTimer = null
      }
    }

    this.ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        const type = msg.type
        if (this.listeners.has(type)) {
          this.listeners.get(type).forEach(cb => cb(msg.data))
        }
      } catch (e) {
        console.error('[WS] 消息解析失败:', e)
      }
    }

    this.ws.onclose = () => {
      console.log('[WS] 连接关闭，3秒后重连...')
      this.reconnectTimer = setTimeout(() => this.connect(), 3000)
    }

    this.ws.onerror = (err) => {
      console.error('[WS] 连接异常:', err)
    }
  }

  on(type, callback) {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, [])
    }
    this.listeners.get(type).push(callback)
  }

  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
    }
    if (this.ws) {
      this.ws.close()
    }
  }
}

export default new DashboardWebSocket()
