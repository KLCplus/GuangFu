<script setup lang="ts">
</script>

<template>
  <section class="api-module-shell">
    <router-view />
  </section>
</template>

<style scoped>
.api-module-shell { min-height: 100%; padding: 24px; background: #f3f5f7; }
@media (max-width: 720px) { .api-module-shell { padding: 16px 12px; } }
</style>
