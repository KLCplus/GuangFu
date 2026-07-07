import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '../store/user'

const AuthLayout = () => import('../layouts/AuthLayout.vue')
const UserLayout = () => import('../layouts/UserLayout.vue')
const AdminLayout = () => import('../layouts/AdminLayout.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/',
    component: AuthLayout,
    meta: { guestOnly: true },
    children: [
      { path: 'login', name: 'Login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
      { path: 'register', name: 'Register', component: () => import('../views/Register.vue'), meta: { title: '注册' } }
    ]
  },
  {
    path: '/',
    component: UserLayout,
    meta: { requiresAuth: true },
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '看板' } },
      { path: 'models/use', name: 'ModelUse', component: () => import('../views/ModelPrediction.vue'), meta: { title: '模型' } },
      { path: 'cloud-forecast', name: 'CloudForecast', component: () => import('../views/CloudForecast.vue'), meta: { title: '云图' } },
      { path: 'marketplace', name: 'Marketplace', component: () => import('../views/Marketplace.vue'), meta: { title: '广场' } },
      { path: 'api', name: 'ApiManage', component: () => import('../views/ApiPlatform.vue'), meta: { title: 'API' } },
      { path: 'reports', name: 'Reports', component: () => import('../views/AnalysisReport.vue'), meta: { title: '报告' } },
      { path: 'news', name: 'NewsList', component: () => import('../views/NewsList.vue'), meta: { title: '新闻' } },
      { path: 'news/:newsId', name: 'NewsDetail', component: () => import('../views/NewsDetail.vue'), meta: { title: '新闻详情' } },
      { path: 'profile', name: 'Profile', component: () => import('../views/Profile.vue'), meta: { title: '我的' } },

      { path: 'stations', name: 'StationList', component: () => import('../views/StationList.vue'), meta: { title: '电站列表' } },
      { path: 'stations/:stationId', name: 'StationDetail', component: () => import('../views/StationDetail.vue'), meta: { title: '电站详情' } },
      { path: 'predictions', name: 'PredictionHistory', component: () => import('../views/PredictionHistory.vue'), meta: { title: '预测历史' } },
      { path: 'predictions/:taskId', name: 'PredictionDetail', component: () => import('../views/PredictionDetail.vue'), meta: { title: '预测详情' } },
      { path: 'data/upload', name: 'DataUpload', component: () => import('../views/DataUpload.vue'), meta: { title: '数据上传' } },

      { path: 'prediction/new', redirect: '/models/use' },
      { path: 'open', redirect: '/api' },
      { path: 'analysis/reports', redirect: '/reports' }
    ]
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, roles: ['ADMIN'] },
    children: [
      { path: '', redirect: '/admin/users-apis' },
      { path: 'users-apis', name: 'AdminUsersApis', component: () => import('../views/admin/UsersApisManage.vue'), meta: { title: '用户/API' } },
      { path: 'stations', name: 'AdminStations', component: () => import('../views/admin/StationManage.vue'), meta: { title: '电站' } },
      { path: 'models', name: 'AdminModels', component: () => import('../views/admin/ModelManage.vue'), meta: { title: '模型' } },

      { path: 'users', redirect: '/admin/users-apis' },
      { path: 'api-keys', redirect: '/admin/users-apis' },
      { path: 'news', component: () => import('../views/admin/NewsManage.vue'), meta: { title: '新闻管理' } }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('../views/Forbidden.vue'),
    meta: { title: '无权访问' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  const isLoggedIn = Boolean(userStore.token)

  if (to.meta.guestOnly && isLoggedIn) {
    return '/dashboard'
  }

  if (to.meta.requiresAuth && !isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const roles = to.meta.roles as string[] | undefined
  if (roles?.length && !roles.some((role) => userStore.hasRole(role))) {
    return '/403'
  }

  document.title = `${to.meta.title || '光伏预测平台'} - 光伏预测平台`
  return true
})

export default router
