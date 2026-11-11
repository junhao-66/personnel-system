<!-- A quiet shared surface keeps public and authenticated pages consistent. -->
<template>
  <div
    aria-hidden="true"
    class="gradient-background"
  />
</template>

<style scoped lang="scss">
.gradient-background {
  position: fixed;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background: var(--bg-page);
}
</style>
