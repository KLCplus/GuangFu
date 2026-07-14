<script setup lang="ts">
import { Bell, Connection, DataLine } from '@element-plus/icons-vue'
import NewsList from './NewsList.vue'
</script>

<template>
  <section class="visual-news-page">
    <header class="intelligence-heading">
      <div class="intelligence-title">
        <span class="intelligence-kicker">INFORMATION INTELLIGENCE CENTER · NEWS</span>
        <h1>新闻通知情报台</h1>
        <p>汇聚行业资讯、气象预警与站内运行消息，保持能源决策链路实时同步。</p>
      </div>
      <div class="intelligence-status-grid" aria-label="信息中心状态">
        <article>
          <el-icon><Connection /></el-icon>
          <span><small>NEWS LINK</small><strong>新闻链路</strong></span>
          <em><i></i>在线</em>
        </article>
        <article>
          <el-icon><Bell /></el-icon>
          <span><small>MESSAGE BUS</small><strong>消息总线</strong></span>
          <em><i></i>同步</em>
        </article>
        <article>
          <el-icon><DataLine /></el-icon>
          <span><small>UPDATE MODE</small><strong>更新模式</strong></span>
          <em><i></i>实时</em>
        </article>
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
