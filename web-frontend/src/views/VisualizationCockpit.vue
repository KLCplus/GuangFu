<script setup lang="ts">
import Visualization from './Visualization.vue'
</script>

<template>
  <section class="visual-cockpit-page">
    <Visualization />
  </section>
</template>

<style scoped>
.visual-cockpit-page {
  width: 100%;
  height: 100vh;
  min-width: 0;
  overflow: hidden;
}

@media (max-width: 700px) {
  .visual-cockpit-page {
    height: auto;
    min-height: 100vh;
    overflow: visible;
  }
}
</style>
