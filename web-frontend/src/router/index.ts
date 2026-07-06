import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/Login.vue'), meta: { plain: true } },
    { path: '/', component: () => import('@/views/Dashboard.vue') },
    { path: '/stations', component: () => import('@/views/StationList.vue') },
    { path: '/stations/:id', component: () => import('@/views/StationDetail.vue') },
    { path: '/prediction', component: () => import('@/views/ModelPrediction.vue') },
    { path: '/prediction/history', component: () => import('@/views/PredictionHistory.vue') },
    { path: '/analysis', component: () => import('@/views/AnalysisReport.vue') },
    { path: '/api-platform', component: () => import('@/views/ApiPlatform.vue') },
    { path: '/news', component: () => import('@/views/NewsList.vue') },
    { path: '/admin/users', component: () => import('@/views/admin/UserManage.vue') },
    { path: '/admin/stations', component: () => import('@/views/admin/StationManage.vue') },
    { path: '/admin/models', component: () => import('@/views/admin/ModelManage.vue') },
    { path: '/admin/news', component: () => import('@/views/admin/NewsManage.vue') }
  ]
})

export default router
