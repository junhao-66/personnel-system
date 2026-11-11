<script setup lang="ts">
import { computed, reactive, ref, useId } from 'vue'
import type { GraphEdge, GraphNode } from '@/types/knowledgeGraph'
const props = defineProps<{ nodes: GraphNode[]; edges: GraphEdge[]; selectedId: string }>()
const emit = defineEmits<{ select: [node: GraphNode] }>()
type Point = { x: number; y: number }
const svg = ref<SVGSVGElement>()
const marker = `arrow-${useId().replace(/:/g, '')}`
const zoom = ref(1)
const pan = reactive({ x: 0, y: 0 })
const moved = reactive<Record<string, Point>>({})
const positions = computed(() =>
  Object.fromEntries(
    props.nodes.map((node, i, all) => {
      const a = ((i - 1) * 2 * Math.PI) / Math.max(1, all.length - 1)
      return [
        node.id,
        moved[node.id] ||
          (i === 0
            ? { x: 400, y: 260 }
            : { x: 400 + 295 * Math.cos(a), y: 260 + 205 * Math.sin(a) }),
      ]
    }),
  ),
)
const point = (event: PointerEvent | WheelEvent): Point => {
  const matrix = svg.value?.getScreenCTM()
  return matrix
    ? new DOMPoint(event.clientX, event.clientY).matrixTransform(matrix.inverse())
    : { x: 400, y: 260 }
}
const scaleAt = (value: number, anchor: Point = { x: 400, y: 260 }) => {
  const next = Math.max(0.5, Math.min(2.5, value)),
    ratio = next / zoom.value
  pan.x = anchor.x - (anchor.x - pan.x) * ratio
  pan.y = anchor.y - (anchor.y - pan.y) * ratio
  zoom.value = next
}
const reset = () => {
  zoom.value = 1
  pan.x = 0
  pan.y = 0
  Object.keys(moved).forEach((id) => delete moved[id])
}
let gesture:
  | {
      pointer: number
      start: Point
      original: Point
      client: Point
      node?: GraphNode
      dragged: boolean
    }
  | undefined
const down = (event: PointerEvent, node?: GraphNode) => {
  if (event.button !== 0 || gesture) return
  gesture = {
    pointer: event.pointerId,
    start: point(event),
    original: node ? { ...positions.value[node.id]! } : { ...pan },
    client: { x: event.clientX, y: event.clientY },
    node,
    dragged: false,
  }
  svg.value?.setPointerCapture(event.pointerId)
}
const move = (event: PointerEvent) => {
  if (!gesture || gesture.pointer !== event.pointerId) return
  const p = point(event),
    dx = p.x - gesture.start.x,
    dy = p.y - gesture.start.y
  gesture.dragged ||=
    Math.hypot(event.clientX - gesture.client.x, event.clientY - gesture.client.y) > 4
  if (!gesture.dragged) return
  if (gesture.node)
    moved[gesture.node.id] = {
      x: gesture.original.x + dx / zoom.value,
      y: gesture.original.y + dy / zoom.value,
    }
  else {
    pan.x = gesture.original.x + dx
    pan.y = gesture.original.y + dy
  }
}
const finish = (event: PointerEvent, cancel = false) => {
  if (!gesture || gesture.pointer !== event.pointerId) return
  const current = gesture
  gesture = undefined
  if (svg.value?.hasPointerCapture(event.pointerId))
    svg.value.releasePointerCapture(event.pointerId)
  if (!cancel && !current.dragged && current.node) emit('select', current.node)
}
const key = (event: KeyboardEvent, node?: GraphNode) => {
  if (node && (event.key === 'Enter' || event.key === ' ')) {
    event.preventDefault()
    event.stopPropagation()
    emit('select', node)
    return
  }
  const delta: Record<string, Point> = {
    ArrowLeft: { x: -20, y: 0 },
    ArrowRight: { x: 20, y: 0 },
    ArrowUp: { x: 0, y: -20 },
    ArrowDown: { x: 0, y: 20 },
  }
  const d = delta[event.key]
  if (d) {
    event.preventDefault()
    event.stopPropagation()
    if (node) {
      const p = positions.value[node.id]!
      moved[node.id] = { x: p.x + d.x, y: p.y + d.y }
    } else {
      pan.x += d.x
      pan.y += d.y
    }
  } else if (['+', '=', '-', '0'].includes(event.key)) {
    event.preventDefault()
    event.stopPropagation()
    if (event.key === '0') reset()
    else scaleAt(zoom.value * (event.key === '-' ? 1 / 1.2 : 1.2))
  }
}
</script>
<template>
  <div class="controls">
    <button @click="scaleAt(zoom * 1.2)">放大</button>
    <button @click="scaleAt(zoom / 1.2)">缩小</button>
    <button @click="reset">重置视图与布局</button>
    <output aria-live="polite">{{ Math.round(zoom * 100) }}%</output>
  </div>
  <svg
    ref="svg"
    viewBox="0 0 800 520"
    tabindex="0"
    role="group"
    aria-label="可拖拽关系图：拖动节点或空白区域，滚轮或加减键缩放，0重置；节点方向键移动，回车展开"
    @pointerdown="down($event)"
    @pointermove="move"
    @pointerup="finish($event)"
    @pointercancel="finish($event, true)"
    @lostpointercapture="finish($event, true)"
    @wheel.prevent="scaleAt(zoom * Math.exp(-$event.deltaY * 0.001), point($event))"
    @keydown="key($event)"
  >
    <defs>
      <marker
        :id="marker"
        viewBox="0 0 10 10"
        refX="28"
        refY="5"
        markerWidth="6"
        markerHeight="6"
        orient="auto-start-reverse"
      >
        <path
          d="M 0 0 L 10 5 L 0 10 z"
          fill="#8493b0"
        />
      </marker>
    </defs>
    <g :transform="`translate(${pan.x},${pan.y}) scale(${zoom})`">
      <line
        v-for="edge in edges"
        :key="edge.id"
        :x1="positions[edge.from]?.x"
        :y1="positions[edge.from]?.y"
        :x2="positions[edge.to]?.x"
        :y2="positions[edge.to]?.y"
        stroke="#a4b8cb"
        :marker-end="`url(#${marker})`"
      />
      <g
        v-for="node in nodes"
        :key="node.id"
        :data-node-id="node.id"
        :transform="`translate(${positions[node.id]!.x},${positions[node.id]!.y})`"
        tabindex="0"
        role="button"
        :aria-label="'展开节点：' + node.label"
        @pointerdown.stop="down($event, node)"
        @keydown="key($event, node)"
        @click="$event.detail === 0 && emit('select', node)"
      >
        <circle
          :r="node.id === selectedId ? 29 : 20"
          :fill="
            node.id === selectedId ? 'var(--brand-600)' : node.type === 'QUESTION' ? 'var(--accent-200)' : '#c4d7fc'
          "
        />
        <text
          y="43"
          text-anchor="middle"
        >
          {{ node.label.length > 12 ? node.label.slice(0, 12) + '…' : node.label }}
        </text>
        <title>{{ node.label }}</title>
      </g>
    </g>
  </svg>
  <p class="hint">
    拖动节点调整布局，拖动空白平移；滚轮或按钮缩放。键盘方向键移动，加减键缩放，0重置。移动端可用按钮缩放。
  </p>
</template>
<style scoped lang="scss">
.controls {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
button {
  cursor: pointer;
  padding: 8px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: white;
  color: var(--brand-600);
}
svg {
  width: 100%;
  display: block;
  touch-action: none;
  user-select: none;
  cursor: grab;
}
svg g[role='button'] {
  cursor: move;
}
svg text {
  font-size: 13px;
  fill: var(--brand-700);
}
svg:focus-visible,
button:focus-visible {
  outline: 3px solid var(--brand-500);
}
g:focus-visible circle {
  stroke: var(--brand-500);
  stroke-width: 4px;
}
.hint {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
}
</style>
