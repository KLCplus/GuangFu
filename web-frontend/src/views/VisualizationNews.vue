<script setup lang="ts">
import NewsList from './NewsList.vue'
</script>

<template>
  <section class="visual-news-page">
    <header class="intelligence-heading">
      <div class="intelligence-icon" aria-hidden="true">
        <img src="/images/news.png" alt="" />
        <i></i>
      </div>
      <div class="intelligence-title">
        <h1>资讯与消息</h1>
        <p>汇总行业资讯、政策动态与平台消息。</p>
      </div>
      <div class="intelligence-axis" aria-hidden="true"><i></i><b></b></div>
    </header>

    <section class="intelligence-panel">
      <span class="panel-corner corner-tl" aria-hidden="true"></span>
      <span class="panel-corner corner-br" aria-hidden="true"></span>
      <NewsList base-path="/visualization-ui/news" />
    </section>
  </section>
</template>

<style src="../styles/visualization-news.css"></style>
