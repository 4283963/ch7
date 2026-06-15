<template>
  <div class="dashboard">
    <header class="dashboard-header">
      <h1>🌧️ 老旧小区集中式雨水集蓄与多井液压平衡调配系统</h1>
      <span class="conn-status" :class="{ connected: wsConnected }">
        {{ wsConnected ? '● 实时在线' : '○ 连接中断' }}
      </span>
    </header>

    <div class="dashboard-body">
      <div class="well-grid">
        <div v-for="well in wells" :key="well.id" class="well-card">
          <div class="well-title">{{ well.name }}</div>
          <canvas
            :ref="el => setCanvasRef(well.id, el)"
            :width="canvasSize.width"
            :height="canvasSize.height"
          ></canvas>
          <div class="well-info">
            <span class="level-text">水位: {{ displayLevels[well.id] }} cm</span>
            <span class="pump-badge" :class="pumpClass(well.id)">
              💧 抽水泵 {{ pumpStates[well.id] ? '运转中' : '待机' }}
            </span>
          </div>
        </div>
      </div>

      <div class="valve-panel">
        <h3>液压连通阀状态</h3>
        <div class="valve-list">
          <div v-for="(open, key) in valveStates" :key="key" class="valve-item" :class="{ active: open }">
            <span class="valve-label">{{ key }} 号阀</span>
            <span class="valve-status">{{ open ? '🔓 开启' : '🔒 关闭' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import dashboardWs from '../utils/websocket.js'

const MAX_LEVEL = 600
const canvasSize = { width: 220, height: 400 }

const LERP_SPEED = 0.08
const DISPLAY_THROTTLE_MS = 200

const wells = reactive([
  { id: 1, name: '1 号深井' },
  { id: 2, name: '2 号深井' },
  { id: 3, name: '3 号深井' },
])

const targetLevels = reactive({ 1: 0, 2: 0, 3: 0 })
const currentLevels = { 1: 0, 2: 0, 3: 0 }
const displayLevels = reactive({ 1: 0, 2: 0, 3: 0 })

const pumpStates = reactive({ 1: false, 2: false, 3: false })
const valveStates = reactive({ '1-2': false, '1-3': false, '2-3': false })
const wsConnected = ref(false)

const canvasRefs = {}

let lastDisplayUpdate = 0

function setCanvasRef(wellId, el) {
  if (el) canvasRefs[wellId] = el
}

function pumpClass(wellId) {
  return {
    'pump-running': pumpStates[wellId],
    'pump-idle': !pumpStates[wellId],
  }
}

function drawWell(wellId) {
  const canvas = canvasRefs[wellId]
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const w = canvas.width
  const h = canvas.height

  ctx.clearRect(0, 0, w, h)

  const padding = 30
  const barX = padding
  const barW = w - padding * 2
  const barY = 30
  const barH = h - 80

  ctx.strokeStyle = '#2a3a5c'
  ctx.lineWidth = 2
  ctx.strokeRect(barX, barY, barW, barH)

  const level = currentLevels[wellId] || 0
  const levelRatio = Math.min(level / MAX_LEVEL, 1)
  const waterH = barH * levelRatio

  let gradient
  if (level > 450) {
    gradient = ctx.createLinearGradient(0, barY + barH - waterH, 0, barY + barH)
    gradient.addColorStop(0, '#ff4444')
    gradient.addColorStop(1, '#cc0000')
  } else if (level > 300) {
    gradient = ctx.createLinearGradient(0, barY + barH - waterH, 0, barY + barH)
    gradient.addColorStop(0, '#ffaa00')
    gradient.addColorStop(1, '#ff6600')
  } else {
    gradient = ctx.createLinearGradient(0, barY + barH - waterH, 0, barY + barH)
    gradient.addColorStop(0, '#00ccff')
    gradient.addColorStop(1, '#0066cc')
  }

  ctx.fillStyle = gradient
  ctx.fillRect(barX + 1, barY + barH - waterH, barW - 2, waterH)

  const now = Date.now() / 1000
  for (let i = 0; i < 2; i++) {
    const waveY = barY + barH - waterH + Math.sin(now * 1.5 + i * 2) * 3
    ctx.beginPath()
    ctx.moveTo(barX + 1, waveY)
    for (let x = barX + 1; x < barX + barW - 1; x += 6) {
      const wy = waveY + Math.sin((x - barX) * 0.04 + now * 2 + i) * 2
      ctx.lineTo(x, wy)
    }
    ctx.strokeStyle = 'rgba(255,255,255,0.12)'
    ctx.lineWidth = 1
    ctx.stroke()
  }

  ctx.fillStyle = '#8899aa'
  ctx.font = '11px sans-serif'
  ctx.textAlign = 'right'
  for (let lvl = 0; lvl <= MAX_LEVEL; lvl += 150) {
    const y = barY + barH - (lvl / MAX_LEVEL) * barH
    ctx.fillText(lvl + '', barX - 4, y + 4)
    ctx.beginPath()
    ctx.moveTo(barX, y)
    ctx.lineTo(barX + barW, y)
    ctx.strokeStyle = 'rgba(255,255,255,0.06)'
    ctx.lineWidth = 1
    ctx.stroke()
  }

  ctx.fillStyle = '#ffffff'
  ctx.font = 'bold 20px sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText(Math.round(level) + ' cm', w / 2, barY + barH + 40)

  if (pumpStates[wellId]) {
    ctx.fillStyle = '#00ff88'
    ctx.font = '14px sans-serif'
    ctx.fillText('💧 运转中', w / 2, barY + barH + 58)
  }
}

let animFrameId = null

function animate() {
  for (const wellId of [1, 2, 3]) {
    const target = targetLevels[wellId] || 0
    const current = currentLevels[wellId] || 0
    const diff = target - current

    if (Math.abs(diff) > 0.1) {
      currentLevels[wellId] = current + diff * LERP_SPEED
    } else {
      currentLevels[wellId] = target
    }

    drawWell(wellId)
  }

  const now = Date.now()
  if (now - lastDisplayUpdate >= DISPLAY_THROTTLE_MS) {
    lastDisplayUpdate = now
    for (const wellId of [1, 2, 3]) {
      displayLevels[wellId] = Math.round(currentLevels[wellId])
    }
  }

  animFrameId = requestAnimationFrame(animate)
}

function handleWaterLevel(data) {
  wsConnected.value = true
  for (const [id, level] of Object.entries(data)) {
    targetLevels[Number(id)] = level
  }
}

function handlePumpStatus(data) {
  for (const [id, running] of Object.entries(data)) {
    pumpStates[Number(id)] = running
  }
}

function handleValveStatus(data) {
  for (const [key, open] of Object.entries(data)) {
    valveStates[key] = open
  }
}

onMounted(async () => {
  dashboardWs.on('water_level', handleWaterLevel)
  dashboardWs.on('pump_status', handlePumpStatus)
  dashboardWs.on('valve_status', handleValveStatus)
  dashboardWs.connect()

  try {
    const resp = await fetch('/api/water-levels/latest')
    if (resp.ok) {
      const data = await resp.json()
      for (const [id, level] of Object.entries(data)) {
        const wellId = Number(id)
        targetLevels[wellId] = level
        currentLevels[wellId] = level
        displayLevels[wellId] = level
      }
    }
  } catch (e) {
    console.warn('初始水位加载失败:', e)
  }

  try {
    const [pumpResp, valveResp] = await Promise.all([
      fetch('/api/status/pumps'),
      fetch('/api/status/valves'),
    ])
    if (pumpResp.ok) {
      const data = await pumpResp.json()
      for (const [id, running] of Object.entries(data)) {
        pumpStates[Number(id)] = running
      }
    }
    if (valveResp.ok) {
      const data = await valveResp.json()
      for (const [key, open] of Object.entries(data)) {
        valveStates[key] = open
      }
    }
  } catch (e) {
    console.warn('初始状态加载失败:', e)
  }

  await nextTick()
  animate()
})

onBeforeUnmount(() => {
  if (animFrameId) cancelAnimationFrame(animFrameId)
  dashboardWs.disconnect()
})
</script>

<style scoped>
.dashboard {
  min-height: 100vh;
  padding: 20px;
}
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: linear-gradient(135deg, #0d2137 0%, #162d50 100%);
  border-radius: 12px;
  margin-bottom: 24px;
  border: 1px solid #1e3a5f;
}
.dashboard-header h1 {
  font-size: 20px;
  font-weight: 600;
  color: #c8ddf0;
}
.conn-status {
  font-size: 13px;
  color: #666;
}
.conn-status.connected {
  color: #00ff88;
}
.dashboard-body {
  display: flex;
  gap: 24px;
}
.well-grid {
  display: flex;
  gap: 20px;
  flex: 1;
}
.well-card {
  background: linear-gradient(180deg, #0f1f35 0%, #0a1628 100%);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #1e3a5f;
  text-align: center;
  flex: 1;
}
.well-title {
  font-size: 16px;
  font-weight: 600;
  color: #8ab4e0;
  margin-bottom: 8px;
}
canvas {
  display: block;
  margin: 0 auto;
}
.well-info {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
}
.level-text {
  font-size: 13px;
  color: #8899aa;
}
.pump-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  display: inline-block;
}
.pump-running {
  background: rgba(0, 255, 136, 0.15);
  color: #00ff88;
  border: 1px solid rgba(0, 255, 136, 0.3);
  animation: pulse-green 2s infinite;
}
.pump-idle {
  background: rgba(100, 100, 120, 0.15);
  color: #667788;
  border: 1px solid rgba(100, 100, 120, 0.2);
}
@keyframes pulse-green {
  0%, 100% { box-shadow: 0 0 4px rgba(0, 255, 136, 0.2); }
  50% { box-shadow: 0 0 12px rgba(0, 255, 136, 0.5); }
}
.valve-panel {
  width: 220px;
  background: linear-gradient(180deg, #0f1f35 0%, #0a1628 100%);
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #1e3a5f;
  align-self: flex-start;
}
.valve-panel h3 {
  font-size: 15px;
  color: #8ab4e0;
  margin-bottom: 16px;
}
.valve-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.valve-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid #1e3a5f;
  transition: all 0.3s ease;
}
.valve-item.active {
  background: rgba(0, 200, 255, 0.08);
  border-color: rgba(0, 200, 255, 0.3);
}
.valve-label {
  font-size: 13px;
  color: #8899aa;
}
.valve-status {
  font-size: 12px;
}
.valve-item.active .valve-status {
  color: #00ccff;
}
</style>
