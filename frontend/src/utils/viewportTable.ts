import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

// Reserve space for pagination and help beneath the table; never clip the page.
export function useViewportTableHeight(anchor: Ref<HTMLElement | undefined>) {
  const height = ref(280)
  let observer: ResizeObserver | undefined
  const measure = () => {
    if (!anchor.value || !anchor.value.getClientRects().length) return
    height.value = Math.max(
      160,
      Math.floor(window.innerHeight - anchor.value.getBoundingClientRect().top - 160),
    )
  }
  onMounted(() => {
    measure()
    observer = new ResizeObserver(measure)
    const container = anchor.value?.closest('main')
    if (container) observer.observe(container)
    window.addEventListener('resize', measure)
  })
  onBeforeUnmount(() => {
    observer?.disconnect()
    window.removeEventListener('resize', measure)
  })
  return height
}
