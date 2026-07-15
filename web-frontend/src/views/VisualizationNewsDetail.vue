<script setup lang="ts">
import { Document, Link, Position } from '@element-plus/icons-vue'
import NewsDetail from './NewsDetail.vue'
</script>

<template>
  <section class="visual-news-detail-page">
    <header class="detail-intelligence-heading">
      <div>
        <h1>资讯详情</h1>
        <p>保留信息来源、发布时间与原文入口，便于快速核验和追踪。</p>
      </div>
      <dl>
        <div><dt><el-icon><Document /></el-icon>内容状态</dt><dd><i></i>已归档</dd></div>
        <div><dt><el-icon><Link /></el-icon>来源链路</dt><dd><i></i>可追溯</dd></div>
        <div><dt><el-icon><Position /></el-icon>资讯通道</dt><dd><i></i>在线</dd></div>
      </dl>
    </header>

    <section class="detail-intelligence-panel">
      <span class="panel-corner corner-tl" aria-hidden="true"></span>
      <span class="panel-corner corner-br" aria-hidden="true"></span>
      <NewsDetail base-path="/visualization-ui/news" />
    </section>
  </section>
</template>

<style src="../styles/visualization-news-detail.css"></style>
