<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as THREE from 'three'

const host = ref<HTMLDivElement>()
let cleanup: (() => void) | undefined

onMounted(() => {
  const container = host.value
  if (!container) return

  const scene = new THREE.Scene()
  const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100)
  camera.position.set(0, 0, 13)
  const renderer = new THREE.WebGLRenderer({
    alpha: true,
    antialias: false,
    powerPreference: 'low-power',
  })
  renderer.setPixelRatio(Math.min(devicePixelRatio, 1))
  renderer.setClearColor(0xffffff, 0)
  container.appendChild(renderer.domElement)

  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches
  const count = innerWidth < 768 ? 96 : 240
  const positions = new Float32Array(count * 3)
  for (let i = 0; i < count; i += 1) {
    positions[i * 3] = (Math.random() - 0.5) * 24
    positions[i * 3 + 1] = (Math.random() - 0.5) * 15
    positions[i * 3 + 2] = (Math.random() - 0.5) * 8
  }
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  const particles = new THREE.Points(
    geometry,
    new THREE.PointsMaterial({ color: 0x3a9c91, size: 0.035, transparent: true, opacity: 0.2 }),
  )
  scene.add(particles)

  const material = new THREE.MeshBasicMaterial({
    color: 0x6ca9a2,
    wireframe: true,
    transparent: true,
    opacity: 0.075,
  })
  const shapeA = new THREE.Mesh(new THREE.IcosahedronGeometry(1.55, 1), material)
  shapeA.position.set(6.6, 2.8, -1)
  const shapeB = new THREE.Mesh(new THREE.TorusKnotGeometry(0.9, 0.2, 72, 8), material.clone())
  shapeB.position.set(-6.4, -3.1, -2)
  scene.add(shapeA, shapeB)

  const pointer = new THREE.Vector2()
  const resize = () => {
    const width = container.clientWidth
    const height = container.clientHeight
    camera.aspect = width / Math.max(height, 1)
    camera.updateProjectionMatrix()
    renderer.setSize(width, height, false)
    renderer.render(scene, camera)
  }
  const move = (event: PointerEvent) => {
    pointer.set(event.clientX / innerWidth - 0.5, event.clientY / innerHeight - 0.5)
  }
  const observer = new ResizeObserver(resize)
  observer.observe(container)
  window.addEventListener('pointermove', move, { passive: true })
  resize()

  let frame = 0
  let previousFrame = 0
  let paused = document.hidden
  const render = (now = 0) => {
    frame = requestAnimationFrame(render)
    if (paused || now - previousFrame < 50) return
    previousFrame = now
    particles.rotation.y += 0.00035
    shapeA.rotation.x += 0.0007
    shapeA.rotation.y += 0.0009
    shapeB.rotation.x -= 0.00055
    shapeB.rotation.z += 0.0007
    camera.position.x += (pointer.x * 0.35 - camera.position.x) * 0.018
    camera.position.y += (-pointer.y * 0.25 - camera.position.y) * 0.018
    camera.lookAt(0, 0, 0)
    renderer.render(scene, camera)
  }
  const updatePauseState = () => {
    const shell = container.parentElement
    paused =
      document.hidden ||
      Boolean(
        shell?.classList.contains('menu-open') ||
          shell?.classList.contains('nav-forward') ||
          shell?.classList.contains('nav-backward'),
      )
  }
  const shellObserver = new MutationObserver(updatePauseState)
  if (container.parentElement) shellObserver.observe(container.parentElement, { attributes: true })
  document.addEventListener('visibilitychange', updatePauseState)
  if (!reducedMotion) render()

  cleanup = () => {
    cancelAnimationFrame(frame)
    observer.disconnect()
    shellObserver.disconnect()
    window.removeEventListener('pointermove', move)
    document.removeEventListener('visibilitychange', updatePauseState)
    geometry.dispose()
    ;(particles.material as THREE.Material).dispose()
    shapeA.geometry.dispose()
    shapeB.geometry.dispose()
    material.dispose()
    ;(shapeB.material as THREE.Material).dispose()
    renderer.dispose()
    renderer.domElement.remove()
  }
})

onBeforeUnmount(() => cleanup?.())
</script>

<template>
  <div
    ref="host"
    class="ambient-scene"
    aria-hidden="true"
  />
</template>

<style scoped>
.ambient-scene {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}
.ambient-scene::after {
  position: absolute;
  inset: 0;
  content: '';
  opacity: 0.28;
  background-image: radial-gradient(rgb(38 62 58 / 10%) 0.55px, transparent 0.55px);
  background-size: 4px 4px;
}
.ambient-scene canvas {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
