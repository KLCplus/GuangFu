<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getNewsList } from '@/api/news'
const news = ref<Record<string, string>[]>([])
onMounted(async () => { news.value = (await getNewsList()).data.data.records })
</script>
<template><h2 class="page-title">新闻通知</h2><el-card v-for="item in news" :key="item.newsId" style="margin-bottom: 12px"><h3>{{ item.title }}</h3><el-tag>{{ item.type }}</el-tag><p>{{ item.content }}</p></el-card></template>
