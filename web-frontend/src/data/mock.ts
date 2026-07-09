export type StationStatus = 'RUNNING' | 'STOPPED' | 'MAINTENANCE'
export type ModelStatus = 'ONLINE' | 'OFFLINE' | 'TESTING'
export type PredictionStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
export type NewsType = 'MODEL_UPDATE' | 'SYSTEM_NOTICE' | 'OPERATION'
export type ModelCategory = 'TIME_SERIES' | 'VISION_FUSION' | 'VIDEO_RECURSIVE'
export type ApiKeyStatus = 'ACTIVE' | 'DISABLED'

export interface Station {
  stationId: number
  stationName: string
  province: string
  city: string
  address: string
  longitude: number
  latitude: number
  capacity: number
  status: StationStatus
  description: string
}

export interface RealtimeData {
  stationId: number
  collectTime: string
  power: number
  voltage: number
  current: number
  irradiance: number
  temperature: number
  humidity: number
  windSpeed: number
}

export interface HistoryPoint {
  time: string
  power: number
  irradiance: number
  temperature: number
}

export interface WeatherInfo {
  stationId: number
  weather: string
  temperature: number
  humidity: number
  reportTime: string
}

export interface ForecastInfo {
  date: string
  dayWeather: string
  nightWeather: string
  dayTemp: number
  nightTemp: number
}

export interface ModelInfo {
  modelId: number
  modelName: string
  modelCode: string
  modelType: string
  modelVersion: string
  modelStatus: ModelStatus
  description: string
}

export interface MarketplaceModel extends ModelInfo {
  category: ModelCategory
  categoryName: string
  status?: ModelStatus
  tags: string[]
  price: number
  quota: number
  unit: string
  callCount: number
  trialEnabled: boolean
  apiPath: string
  inputSchema: string
  outputSchema: string
  inputWindowMinutes: number
  inputFrameIntervalSeconds: number
  outputSteps: number
  outputStepMinutes: number
  serviceModelName: string
  billingRule: string
}

export interface MockApiKey {
  apiKeyId: number
  keyName: string
  apiKey?: string
  apiKeyPrefix?: string
  status: ApiKeyStatus
  rateLimitPerMinute: number
  dailyQuota: number
  expireTime: string
  lastUsedAt?: string
  createdAt: string
}

export interface MockApiUsageSummary {
  todayCalls: number
  totalCalls: number
  successCalls: number
  failedCalls: number
  errorRate: number
  avgLatency: number
  remainingQuota: number
}

export interface MockApiUsagePoint {
  date: string
  calls: number
  errors: number
  avgLatency: number
}

export interface MockNotificationItem {
  notificationId: number
  title: string
  content: string
  notificationType: string
  relatedType?: string
  relatedId?: number
  readStatus: 0 | 1
  readTime?: string
  createdAt: string
}

export interface MockUserProfile {
  userId: number
  username: string
  nickname: string
  email: string
  phone?: string
  avatarUrl?: string
  gender?: number
  status: number
  roles: string[]
  createdAt: string
}

export interface MockWallet {
  balance: number
  frozenBalance: number
  monthlyCost: number
  currency: 'CNY'
}

export interface MockWalletRecord {
  recordId: number
  type: 'RECHARGE' | 'CONSUME' | 'REFUND'
  amount: number
  title: string
  createdAt: string
}

export interface MockApiEntitlement {
  entitlementId: number
  modelId: number
  modelName: string
  apiKeyName: string
  quotaTotal: number
  quotaUsed: number
  expireTime: string
  status: ApiKeyStatus
}

export interface PredictionResult {
  timeOffset: number
  predictPower: number
}

export interface PredictionTask {
  taskId: number
  stationId: number
  stationName: string
  modelId: number
  modelName: string
  taskStatus: PredictionStatus
  costTime: number
  createTime: string
  predictions?: PredictionResult[]
}

export interface NewsItem {
  newsId: number
  title: string
  type: NewsType
  content: string
  publishTime: string
}

export interface ApiCallLog {
  id: number
  requestTime: string
  apiPath: string
  statusCode: number
  costTime: number
  message: string
}

export interface ReportData {
  stationName: string
  reportTime: string
  summary: string
  weatherAnalysis: string
  predictionAnalysis: string
  suggestion: string
}

export const mockStations: Station[] = [
  {
    stationId: 1,
    stationName: '成都示范光伏电站',
    province: '四川省',
    city: '成都市',
    address: '高新区示范园 A 区',
    longitude: 104.0668,
    latitude: 30.5728,
    capacity: 1200,
    status: 'RUNNING',
    description: '用于短期功率预测和运维分析的示范电站'
  },
  {
    stationId: 2,
    stationName: '德阳屋顶分布式电站',
    province: '四川省',
    city: '德阳市',
    address: '旌阳区工业园 12 号楼',
    longitude: 104.398,
    latitude: 31.127,
    capacity: 860,
    status: 'RUNNING',
    description: '工业园区屋顶分布式光伏项目'
  },
  {
    stationId: 3,
    stationName: '绵阳储能协同电站',
    province: '四川省',
    city: '绵阳市',
    address: '科技城新区光伏基地',
    longitude: 104.7417,
    latitude: 31.464,
    capacity: 1500,
    status: 'MAINTENANCE',
    description: '含储能协同调度的复合型电站'
  },
  {
    stationId: 4,
    stationName: '眉山农业光伏电站',
    province: '四川省',
    city: '眉山市',
    address: '东坡区农业示范园',
    longitude: 103.8485,
    latitude: 30.0754,
    capacity: 980,
    status: 'STOPPED',
    description: '农业大棚上方光伏发电场景'
  }
]

export const mockRealtime: RealtimeData = {
  stationId: 1,
  collectTime: '2026-07-06 10:30:00',
  power: 523.5,
  voltage: 380.2,
  current: 120.6,
  irradiance: 850,
  temperature: 31.5,
  humidity: 62,
  windSpeed: 2.5
}

export const mockHistory: HistoryPoint[] = [
  { time: '10:00', power: 500.2, irradiance: 820.5, temperature: 31.2 },
  { time: '10:05', power: 510.4, irradiance: 833.1, temperature: 31.3 },
  { time: '10:10', power: 516.8, irradiance: 838.4, temperature: 31.4 },
  { time: '10:15', power: 522.1, irradiance: 846.2, temperature: 31.5 },
  { time: '10:20', power: 530.6, irradiance: 858.8, temperature: 31.7 },
  { time: '10:25', power: 536.9, irradiance: 866.4, temperature: 31.8 },
  { time: '10:30', power: 548.3, irradiance: 879.9, temperature: 32 }
]

export const mockWeather: WeatherInfo = {
  stationId: 1,
  weather: '晴',
  temperature: 32,
  humidity: 60,
  reportTime: '2026-07-06 10:30:00'
}

export const mockForecast: ForecastInfo[] = [
  { date: '2026-07-06', dayWeather: '晴', nightWeather: '多云', dayTemp: 34, nightTemp: 26 },
  { date: '2026-07-07', dayWeather: '多云', nightWeather: '阵雨', dayTemp: 32, nightTemp: 25 },
  { date: '2026-07-08', dayWeather: '晴', nightWeather: '晴', dayTemp: 35, nightTemp: 26 }
]

export const mockModels: ModelInfo[] = [
  {
    modelId: 1,
    modelName: 'LSTM 光伏功率预测模型',
    modelCode: 'lstm_v1',
    modelType: 'NUMERIC',
    modelVersion: 'v1.0',
    modelStatus: 'ONLINE',
    description: '适用于未来 30 分钟短期功率预测'
  },
  {
    modelId: 2,
    modelName: 'Transformer 短时预测模型',
    modelCode: 'transformer_short_v2',
    modelType: 'NUMERIC',
    modelVersion: 'v2.1',
    modelStatus: 'TESTING',
    description: '融合天气和历史功率序列的测试模型'
  },
  {
    modelId: 3,
    modelName: '基线回归预测模型',
    modelCode: 'baseline_regression',
    modelType: 'NUMERIC',
    modelVersion: 'v0.9',
    modelStatus: 'OFFLINE',
    description: '用于模型效果对比的离线基线模型'
  }
]

export const modelCategoryOptions: Array<{ label: string; value: ModelCategory }> = [
  { label: '时序基线', value: 'TIME_SERIES' },
  { label: '云图/视觉融合', value: 'VISION_FUSION' },
  { label: '视频时空递归', value: 'VIDEO_RECURSIVE' }
]

type MarketplaceModelSeed = Pick<
  MarketplaceModel,
  'modelName' | 'modelCode' | 'modelType' | 'modelVersion' | 'category' | 'categoryName' | 'tags' | 'description'
> &
  Partial<Pick<MarketplaceModel, 'trialEnabled'>>

const marketplaceModelSeeds: MarketplaceModelSeed[] = [
  {
    modelName: 'PatchTST',
    modelCode: 'PatchTST',
    modelType: 'NUMERIC',
    modelVersion: 'v1.0',
    category: 'TIME_SERIES',
    categoryName: '时序基线',
    tags: ['短期预测', 'Transformer', '功率序列'],
    description: '基于 patch 的时序 Transformer，适合多变量光伏功率短期预测。'
  },
  {
    modelName: 'DLinear',
    modelCode: 'DLinear',
    modelType: 'NUMERIC',
    modelVersion: 'v1.0',
    category: 'TIME_SERIES',
    categoryName: '时序基线',
    tags: ['轻量模型', '趋势分解', '低延迟'],
    description: '轻量线性时序模型，适合做低成本基线预测和效果对照。'
  },
  {
    modelName: 'iTransformer',
    modelCode: 'iTransformer',
    modelType: 'NUMERIC',
    modelVersion: 'v1.0',
    category: 'TIME_SERIES',
    categoryName: '时序基线',
    tags: ['通道建模', '高精度', '生产推荐'],
    description: '面向多变量通道建模的 Transformer，用于分钟级功率预测。'
  },
  {
    modelName: 'TimeXer',
    modelCode: 'TimeXer',
    modelType: 'NUMERIC',
    modelVersion: 'v1.0',
    category: 'TIME_SERIES',
    categoryName: '时序基线',
    tags: ['外生变量', '天气融合', '分钟级'],
    description: '支持外部天气和辐照度变量的时序预测模型。'
  },
  {
    modelName: 'TimeMixer',
    modelCode: 'TimeMixer',
    modelType: 'NUMERIC',
    modelVersion: 'v1.0',
    category: 'TIME_SERIES',
    categoryName: '时序基线',
    tags: ['多尺度', '稳定输出', '趋势识别'],
    description: '多尺度时间模式混合模型，适合波动较大的光伏场景。'
  },
  {
    modelName: 'TSMixer',
    modelCode: 'TSMixer',
    modelType: 'NUMERIC',
    modelVersion: 'v1.0',
    category: 'TIME_SERIES',
    categoryName: '时序基线',
    tags: ['MLP', '快速推理', '批量任务'],
    description: '基于 MLP 的时序混合模型，推理快，适合批量调用。'
  },
  {
    modelName: 'Transformer',
    modelCode: 'Transformer',
    modelType: 'NUMERIC',
    modelVersion: 'v1.0',
    category: 'TIME_SERIES',
    categoryName: '时序基线',
    tags: ['经典结构', '通用预测', '可解释'],
    description: '经典 Transformer 时序预测模型，便于与其他模型横向比较。'
  },
  {
    modelName: 'CNN+MLP',
    modelCode: 'CNN_MLP',
    modelType: 'MULTIMODAL',
    modelVersion: 'v1.0',
    category: 'VISION_FUSION',
    categoryName: '云图/视觉融合',
    tags: ['云图特征', '轻量融合', '快速试用'],
    description: '提取云图空间特征并融合基础气象数据，适合演示视觉增强预测。'
  },
  {
    modelName: 'CNN+LSTM',
    modelCode: 'CNN_LSTM',
    modelType: 'MULTIMODAL',
    modelVersion: 'v1.0',
    category: 'VISION_FUSION',
    categoryName: '云图/视觉融合',
    tags: ['云图序列', 'LSTM', '短时预测'],
    description: '结合云图特征和历史序列状态，用于短时功率变化预测。'
  },
  {
    modelName: '3D-CNN+LSTM',
    modelCode: '3DCNN_LSTM',
    modelType: 'MULTIMODAL',
    modelVersion: 'v1.0',
    category: 'VISION_FUSION',
    categoryName: '云图/视觉融合',
    tags: ['三维卷积', '时空特征', '高阶融合'],
    description: '使用 3D 卷积建模连续云图时空特征，再接入序列预测。'
  },
  {
    modelName: 'convlstm+lstm',
    modelCode: 'ConvLSTM_LSTM',
    modelType: 'MULTIMODAL',
    modelVersion: 'v1.0',
    category: 'VISION_FUSION',
    categoryName: '云图/视觉融合',
    tags: ['ConvLSTM', '视觉序列', '融合预测'],
    description: '以 ConvLSTM 捕捉云层运动，再结合功率序列输出预测。'
  },
  {
    modelName: 'simvp+gsta',
    modelCode: 'SimVP_GSTA',
    modelType: 'VIDEO',
    modelVersion: 'v1.0',
    category: 'VIDEO_RECURSIVE',
    categoryName: '视频时空递归',
    tags: ['云图外推', 'GSTA', '视频预测'],
    description: '用于云图未来帧预测的时空递归模型，可支撑云图演示链路。'
  },
  {
    modelName: 'TAU',
    modelCode: 'TAU',
    modelType: 'VIDEO',
    modelVersion: 'v1.0',
    category: 'VIDEO_RECURSIVE',
    categoryName: '视频时空递归',
    tags: ['视频预测', '时空建模', '云层运动'],
    description: '面向视频时空模式的预测模型，适合连续云图变化建模。'
  },
  {
    modelName: 'PredRNN',
    modelCode: 'PredRNN',
    modelType: 'VIDEO',
    modelVersion: 'v1.0',
    category: 'VIDEO_RECURSIVE',
    categoryName: '视频时空递归',
    tags: ['递归预测', '云图序列', '经典模型'],
    description: '经典视频递归预测模型，用于连续云图帧外推。'
  },
  {
    modelName: 'PredRNN++',
    modelCode: 'PredRNN_PLUS_PLUS',
    modelType: 'VIDEO',
    modelVersion: 'v1.0',
    category: 'VIDEO_RECURSIVE',
    categoryName: '视频时空递归',
    tags: ['递归增强', '长序列', '云图预测'],
    description: 'PredRNN 增强版本，面向更长时间跨度的云图预测。'
  },
  {
    modelName: 'ConvLSTM',
    modelCode: 'ConvLSTM',
    modelType: 'VIDEO',
    modelVersion: 'v1.0',
    category: 'VIDEO_RECURSIVE',
    categoryName: '视频时空递归',
    tags: ['卷积循环', '降雨云团', '时空序列'],
    description: '卷积循环网络，适合云层移动和局部天气变化建模。'
  },
  {
    modelName: 'E3D-LSTM',
    modelCode: 'E3D_LSTM',
    modelType: 'VIDEO',
    modelVersion: 'v1.0',
    category: 'VIDEO_RECURSIVE',
    categoryName: '视频时空递归',
    tags: ['3D 记忆', '高维时空', '云图外推'],
    description: '增强 3D LSTM 结构，适合高维云图时空预测。'
  },
  {
    modelName: 'swinLSTM',
    modelCode: 'SwinLSTM',
    modelType: 'VIDEO',
    modelVersion: 'v1.0',
    category: 'VIDEO_RECURSIVE',
    categoryName: '视频时空递归',
    tags: ['Swin', '窗口注意力', '视频预测'],
    description: '结合 Swin 注意力和循环结构，适合云图局部运动建模。'
  },
  {
    modelName: 'sunset',
    modelCode: 'Sunset',
    modelType: 'VIDEO',
    modelVersion: 'v1.0',
    category: 'VIDEO_RECURSIVE',
    categoryName: '视频时空递归',
    tags: ['太阳辐照', '遥感云图', '研究模型'],
    description: '面向太阳能预测数据集的云图时空预测模型。'
  }
]

export const mockMarketplaceModels: MarketplaceModel[] = marketplaceModelSeeds.map((item, index) => ({
  ...item,
  modelId: index + 1,
  modelStatus: index % 9 === 7 ? 'TESTING' : 'ONLINE',
  price: item.category === 'TIME_SERIES' ? 39 : item.category === 'VISION_FUSION' ? 69 : 89,
  quota: item.category === 'TIME_SERIES' ? 10000 : item.category === 'VISION_FUSION' ? 5000 : 3000,
  unit: '次/月',
  callCount: 2400 + index * 317,
  trialEnabled: item.trialEnabled ?? (item.category !== 'VIDEO_RECURSIVE' || index % 2 === 0),
  apiPath: '/openapi/v1/predict',
  inputSchema: '{"input":[{"time":"yyyy-MM-dd HH:mm:ss","power":0,"temperature":0,"irradiance":0}]}',
  outputSchema: '{"predictions":[{"timeOffset":5,"predictPower":0}]}',
  inputWindowMinutes: 30,
  inputFrameIntervalSeconds: 60,
  outputSteps: 6,
  outputStepMinutes: 5,
  serviceModelName: item.modelCode,
  billingRule: `¥${item.category === 'TIME_SERIES' ? 39 : item.category === 'VISION_FUSION' ? 69 : 89}/${item.category === 'TIME_SERIES' ? 10000 : item.category === 'VISION_FUSION' ? 5000 : 3000} 次`
}))

export const mockPredictionResults: PredictionResult[] = [
  { timeOffset: 5, predictPower: 530.2 },
  { timeOffset: 10, predictPower: 535.6 },
  { timeOffset: 15, predictPower: 541.9 },
  { timeOffset: 20, predictPower: 547.4 },
  { timeOffset: 25, predictPower: 552.1 },
  { timeOffset: 30, predictPower: 558.6 }
]

export const mockPredictions: PredictionTask[] = [
  {
    taskId: 1001,
    stationId: 1,
    stationName: '成都示范光伏电站',
    modelId: 1,
    modelName: 'lstm_v1',
    taskStatus: 'SUCCESS',
    costTime: 120,
    createTime: '2026-07-06 10:35:00',
    predictions: mockPredictionResults
  },
  {
    taskId: 1002,
    stationId: 2,
    stationName: '德阳屋顶分布式电站',
    modelId: 1,
    modelName: 'lstm_v1',
    taskStatus: 'RUNNING',
    costTime: 68,
    createTime: '2026-07-06 10:20:00'
  },
  {
    taskId: 1003,
    stationId: 3,
    stationName: '绵阳储能协同电站',
    modelId: 2,
    modelName: 'transformer_short_v2',
    taskStatus: 'FAILED',
    costTime: 0,
    createTime: '2026-07-06 09:55:00'
  },
  {
    taskId: 1004,
    stationId: 1,
    stationName: '成都示范光伏电站',
    modelId: 1,
    modelName: 'lstm_v1',
    taskStatus: 'SUCCESS',
    costTime: 115,
    createTime: '2026-07-06 09:30:00'
  }
]

export const mockNews: NewsItem[] = [
  {
    newsId: 1,
    title: '模型更新通知',
    type: 'MODEL_UPDATE',
    publishTime: '2026-07-06 09:20:00',
    content: '平台新增 Transformer 短时预测模型，当前处于测试中，可在模型管理中查看运行状态。'
  },
  {
    newsId: 2,
    title: '平台维护公告',
    type: 'SYSTEM_NOTICE',
    publishTime: '2026-07-05 18:00:00',
    content: '系统将于本周三凌晨进行例行维护，维护期间预测任务可能短暂延迟。'
  },
  {
    newsId: 3,
    title: '天气接口升级说明',
    type: 'SYSTEM_NOTICE',
    publishTime: '2026-07-05 10:30:00',
    content: '天气数据接口已完成字段对齐，电站详情和综合报告中的天气分析将使用统一数据源。'
  }
]

export const mockCallLogs: ApiCallLog[] = [
  {
    id: 1,
    requestTime: '2026-07-06 10:28:12',
    apiPath: '/openapi/v1/predict',
    statusCode: 200,
    costTime: 132,
    message: '预测成功'
  },
  {
    id: 2,
    requestTime: '2026-07-06 10:14:38',
    apiPath: '/openapi/v1/predict',
    statusCode: 400,
    costTime: 24,
    message: '输入数据不足'
  },
  {
    id: 3,
    requestTime: '2026-07-06 09:51:05',
    apiPath: '/openapi/v1/predict',
    statusCode: 200,
    costTime: 118,
    message: '预测成功'
  }
]

export const mockApiKeys: MockApiKey[] = [
  {
    apiKeyId: 1,
    keyName: 'iTransformer 生产调用 Key',
    apiKey: 'pv_mock_itransformer_********************************',
    apiKeyPrefix: 'pv_mock_i',
    status: 'ACTIVE',
    rateLimitPerMinute: 60,
    dailyQuota: 10000,
    expireTime: '2026-12-31 23:59:59',
    lastUsedAt: '2026-07-08 18:42:10',
    createdAt: '2026-07-01 09:30:00'
  },
  {
    apiKeyId: 2,
    keyName: '云图融合模型试用 Key',
    apiKey: 'pv_mock_vision_********************************',
    apiKeyPrefix: 'pv_mock_v',
    status: 'ACTIVE',
    rateLimitPerMinute: 20,
    dailyQuota: 1000,
    expireTime: '2026-08-31 23:59:59',
    lastUsedAt: '2026-07-07 16:20:35',
    createdAt: '2026-07-04 14:15:00'
  },
  {
    apiKeyId: 3,
    keyName: '历史测试 Key',
    apiKeyPrefix: 'pv_mock_h',
    status: 'DISABLED',
    rateLimitPerMinute: 10,
    dailyQuota: 300,
    expireTime: '2026-09-15 23:59:59',
    createdAt: '2026-06-20 11:00:00'
  }
]

export const mockApiUsageSummary: MockApiUsageSummary = {
  todayCalls: 238,
  totalCalls: 18420,
  successCalls: 17986,
  failedCalls: 434,
  errorRate: 2.36,
  avgLatency: 126,
  remainingQuota: 8762
}

export const mockApiUsageSeries: MockApiUsagePoint[] = [
  { date: '07-03', calls: 186, errors: 5, avgLatency: 132 },
  { date: '07-04', calls: 212, errors: 4, avgLatency: 128 },
  { date: '07-05', calls: 168, errors: 6, avgLatency: 141 },
  { date: '07-06', calls: 246, errors: 7, avgLatency: 124 },
  { date: '07-07', calls: 259, errors: 8, avgLatency: 121 },
  { date: '07-08', calls: 231, errors: 3, avgLatency: 119 },
  { date: '07-09', calls: 238, errors: 4, avgLatency: 126 }
]

export const mockNotifications: MockNotificationItem[] = [
  {
    notificationId: 1,
    title: 'API Key 即将到期',
    content: '云图融合模型试用 Key 将在 30 天内到期，请及时续期或重新申请。',
    notificationType: 'API_QUOTA',
    relatedType: 'API_KEY',
    relatedId: 2,
    readStatus: 0,
    createdAt: '2026-07-09 09:30:00'
  },
  {
    notificationId: 2,
    title: '模型服务升级完成',
    content: 'iTransformer 和 TimeMixer 已完成推理服务升级，平均响应时延预计下降 10%。',
    notificationType: 'MODEL_UPDATE',
    relatedType: 'MODEL',
    relatedId: 3,
    readStatus: 0,
    createdAt: '2026-07-08 18:00:00'
  },
  {
    notificationId: 3,
    title: '调用额度提醒',
    content: '本月 API 调用额度使用超过 70%，可在 API 管理页查看详情。',
    notificationType: 'QUOTA_ALERT',
    relatedType: 'API_KEY',
    relatedId: 1,
    readStatus: 1,
    readTime: '2026-07-08 10:12:00',
    createdAt: '2026-07-07 20:15:00'
  }
]

export const mockUserProfile: MockUserProfile = {
  userId: 1,
  username: 'demo',
  nickname: '光伏用户',
  email: 'demo@example.com',
  phone: '13800000000',
  avatarUrl: '',
  gender: 0,
  status: 1,
  roles: ['USER', 'API_USER'],
  createdAt: '2026-07-01 09:00:00'
}

export const mockWallet: MockWallet = {
  balance: 268.8,
  frozenBalance: 0,
  monthlyCost: 132.5,
  currency: 'CNY'
}

export const mockWalletRecords: MockWalletRecord[] = [
  { recordId: 1, type: 'RECHARGE', amount: 300, title: '账户充值', createdAt: '2026-07-01 10:00:00' },
  { recordId: 2, type: 'CONSUME', amount: -39, title: '购买 PatchTST 月度额度', createdAt: '2026-07-03 13:20:00' },
  { recordId: 3, type: 'CONSUME', amount: -31.2, title: '开放 API 调用扣费', createdAt: '2026-07-08 19:30:00' }
]

export const mockApiEntitlements: MockApiEntitlement[] = [
  {
    entitlementId: 1,
    modelId: 3,
    modelName: 'iTransformer',
    apiKeyName: 'iTransformer 生产调用 Key',
    quotaTotal: 10000,
    quotaUsed: 1238,
    expireTime: '2026-12-31 23:59:59',
    status: 'ACTIVE'
  },
  {
    entitlementId: 2,
    modelId: 8,
    modelName: 'CNN+MLP',
    apiKeyName: '云图融合模型试用 Key',
    quotaTotal: 1000,
    quotaUsed: 126,
    expireTime: '2026-08-31 23:59:59',
    status: 'ACTIVE'
  }
]

export const mockReport: ReportData = {
  stationName: '成都示范光伏电站',
  reportTime: '2026-07-06 10:35:00',
  summary: '当前电站运行状态良好，实时功率保持在近 30 分钟高位区间。',
  weatherAnalysis: '当前天气晴朗，辐照度稳定上升，对短时发电效率有正向影响。',
  predictionAnalysis: '未来 30 分钟预测功率小幅上升，峰值预计接近 559 kW。',
  suggestion: '继续监测天气变化，并关注维护中电站的恢复时间。'
}

export function unwrapData<T>(payload: unknown, fallback: T): T {
  const response = payload as { data?: { data?: unknown } }
  const data = response?.data?.data ?? response?.data ?? payload
  return (data ?? fallback) as T
}

export function unwrapRecords<T>(payload: unknown, fallback: T[]): T[] {
  const data = unwrapData<unknown>(payload, fallback)
  if (Array.isArray(data)) return data as T[]
  if (data && typeof data === 'object' && Array.isArray((data as { records?: unknown }).records)) {
    return (data as { records: T[] }).records
  }
  return fallback
}
