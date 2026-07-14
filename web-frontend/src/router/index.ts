import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '../store/user'

const AuthLayout = () => import('../layouts/AuthLayout.vue')
const UserLayout = () => import('../layouts/UserLayout.vue')
const AdminLayout = () => import('../layouts/AdminLayout.vue')
const VisualizationLayout = () => import('../layouts/VisualizationLayout.vue')
const VisualizationModulePage = () => import('../views/VisualizationModulePage.vue')
const VisualizationAdminLayout = () => import('../layouts/VisualizationAdminLayout.vue')
const VisualizationAdminPage = () => import('../views/VisualizationAdminPage.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/welcome'
  },
  {
    path: '/welcome',
    name: 'Welcome',
    component: () => import('../views/Welcome.vue'),
    meta: { guestOnly: true, title: '欢迎' }
  },
  {
    path: '/visualization',
    name: 'Visualization',
    component: () => import('../views/Visualization.vue'),
    meta: { requiresAuth: true, title: '光伏能源运行驾驶舱' }
  },
  {
    path: '/visualization-ui',
    component: VisualizationLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/visualization-ui/cockpit' },
      {
        path: 'cockpit',
        name: 'VisualizationCockpit',
        component: () => import('../views/VisualizationCockpit.vue'),
        meta: { title: '光伏能源运行驾驶舱' }
      },
      {
        path: 'pvoutput',
        name: 'VisualizationPvOutput',
        component: VisualizationModulePage,
        meta: { title: '公开电站观测台', visualModule: 'pvoutput' }
      },
      {
        path: 'weather',
        name: 'VisualizationWeather',
        component: VisualizationModulePage,
        meta: { title: '气象监测中心', visualModule: 'weather' }
      },
      {
        path: 'models/use',
        name: 'VisualizationModelUse',
        component: VisualizationModulePage,
        meta: { title: '模型预测控制台', visualModule: 'model' }
      },
      {
        path: 'cloud-forecast',
        name: 'VisualizationCloudForecast',
        component: VisualizationModulePage,
        meta: { title: '云图预测控制台', visualModule: 'cloud' }
      },
      {
        path: 'marketplace',
        name: 'VisualizationMarketplace',
        component: VisualizationModulePage,
        meta: { title: '模型能力广场', visualModule: 'marketplace' }
      },
      {
        path: 'api/overview',
        name: 'VisualizationApiOverview',
        component: VisualizationModulePage,
        meta: { title: 'API 开放平台', visualModule: 'api-overview' }
      },
      {
        path: 'api/keys',
        name: 'VisualizationApiKeys',
        component: VisualizationModulePage,
        meta: { title: 'API Key 控制台', visualModule: 'api-keys' }
      },
      {
        path: 'api/billing',
        name: 'VisualizationApiBilling',
        component: VisualizationModulePage,
        meta: { title: '余额与流水中心', visualModule: 'api-billing' }
      },
      {
        path: 'api/usage',
        name: 'VisualizationApiUsage',
        component: VisualizationModulePage,
        meta: { title: 'API 使用统计', visualModule: 'api-usage' }
      },
      {
        path: 'reports',
        name: 'VisualizationReports',
        component: VisualizationModulePage,
        meta: { title: 'Agent 分析工作台', visualModule: 'reports' }
      },
      {
        path: 'news',
        name: 'VisualizationNews',
        component: () => import('../views/VisualizationNews.vue'),
        meta: { title: '新闻通知情报台' }
      },
      {
        path: 'news/:newsId',
        name: 'VisualizationNewsDetail',
        component: () => import('../views/VisualizationNewsDetail.vue'),
        meta: { title: '新闻情报详情' }
      },
      {
        path: 'profile',
        name: 'VisualizationProfile',
        component: () => import('../views/VisualizationProfile.vue'),
        meta: { title: '我的能源账户' }
      },
      { path: 'notifications', redirect: { path: '/visualization-ui/news', query: { tab: 'notifications' } } },
      { path: 'prediction/new', redirect: '/visualization-ui/models/use' },
      { path: 'open', redirect: '/visualization-ui/api/overview' },
      { path: 'analysis/reports', redirect: '/visualization-ui/reports' }
    ]
  },
  {
    path: '/visualization-admin',
    component: VisualizationAdminLayout,
    meta: { requiresAuth: true, roles: ['ADMIN'] },
    children: [
      { path: '', redirect: '/visualization-admin/users-apis' },
      {
        path: 'users-apis',
        name: 'VisualizationAdminUsersApis',
        component: VisualizationAdminPage,
        meta: { title: '用户与 API 管理', visualAdminModule: 'users-apis' }
      },
      {
        path: 'pvoutput',
        name: 'VisualizationAdminPvOutput',
        component: VisualizationAdminPage,
        meta: { title: '数据源管理', visualAdminModule: 'pvoutput' }
      },
      {
        path: 'models',
        name: 'VisualizationAdminModels',
        component: VisualizationAdminPage,
        meta: { title: '模型管理', visualAdminModule: 'models' }
      },
      {
        path: 'news',
        name: 'VisualizationAdminNews',
        component: VisualizationAdminPage,
        meta: { title: '新闻管理', visualAdminModule: 'news' }
      },
      {
        path: 'announcements',
        name: 'VisualizationAdminAnnouncements',
        component: VisualizationAdminPage,
        meta: { title: '公告管理', visualAdminModule: 'announcements' }
      },
      { path: 'api-keys', redirect: '/visualization-admin/users-apis' }
    ]
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
      { path: 'visualization', name: 'Visualization', component: () => import('../views/Visualization.vue'), meta: { title: '数据可视化' } },
      { path: 'models/use', name: 'ModelUse', component: () => import('../views/ModelPrediction.vue'), meta: { title: '模型' } },
      { path: 'cloud-forecast', name: 'CloudForecast', component: () => import('../views/CloudForecast.vue'), meta: { title: '云图' } },
      { path: 'weather', name: 'Weather', component: () => import('../views/Weather.vue'), meta: { title: '天气' } },
      { path: 'pvoutput', name: 'PublicPvOutput', redirect: '/dashboard', meta: { title: '公开电站' } },
      { path: 'marketplace', name: 'Marketplace', component: () => import('../views/Marketplace.vue'), meta: { title: '广场' } },
      {
        path: 'api',
        component: () => import('../layouts/ApiLayout.vue'),
        meta: { title: 'API' },
        children: [
          { path: '', redirect: '/api/overview' },
          { path: 'overview', name: 'ApiOverview', component: () => import('../views/ApiPlatform.vue'), meta: { title: 'API 概览' } },
          { path: 'keys', name: 'ApiKeys', component: () => import('../views/ApiPlatform.vue'), meta: { title: 'API Keys' } },
          { path: 'billing', name: 'ApiBilling', component: () => import('../views/ApiPlatform.vue'), meta: { title: '余额与流水' } },
          { path: 'usage', name: 'ApiUsage', component: () => import('../views/ApiPlatform.vue'), meta: { title: '使用统计' } },
        ]
      },
      { path: 'reports', name: 'Reports', component: () => import('../views/AnalysisReport.vue'), meta: { title: '报告' } },
      { path: 'news', name: 'NewsList', component: () => import('../views/NewsList.vue'), meta: { title: '新闻' } },
      { path: 'news/:newsId', name: 'NewsDetail', component: () => import('../views/NewsDetail.vue'), meta: { title: '新闻详情' } },
      { path: 'notifications', name: 'Notifications', redirect: { path: '/news', query: { tab: 'notifications' } }, meta: { title: '站内通知' } },
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
      { path: 'users-apis', name: 'AdminUsersApis', component: () => import('../views/admin/UsersApisManage.vue'), meta: { title: 'API 管理' } },
      { path: 'pvoutput', name: 'AdminPvOutput', component: () => import('../views/admin/PvOutputManage.vue'), meta: { title: 'PVOutput' } },
      { path: 'models', name: 'AdminModels', component: () => import('../views/admin/ModelManage.vue'), meta: { title: '模型' } },

      { path: 'api-keys', redirect: '/admin/users-apis' },
      { path: 'news', component: () => import('../views/admin/NewsManage.vue'), meta: { title: '新闻管理' } },
      { path: 'announcements', name: 'AdminAnnouncements', component: () => import('../views/admin/AnnouncementManage.vue'), meta: { title: '公告管理' } }
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

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const isLoggedIn = Boolean(userStore.token)

  if (to.meta.guestOnly && isLoggedIn) {
    return userStore.hasRole('ADMIN') ? '/admin' : '/dashboard'
  }

  if (to.meta.requiresAuth && !isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录但角色信息为空时，从后端刷新用户信息
  if (isLoggedIn && !userStore.userInfo.roles?.length) {
    await userStore.fetchProfile()
  }

  const roles = to.meta.roles as string[] | undefined
  if (roles?.length && !roles.some((role) => userStore.hasRole(role))) {
    return '/403'
  }

  document.title = `${to.meta.title || '光伏预测平台'} - 光伏预测平台`
  return true
})

export default router
