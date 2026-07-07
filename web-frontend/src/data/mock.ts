export type StationStatus = 'RUNNING' | 'STOPPED' | 'MAINTENANCE'
export type ModelStatus = 'ONLINE' | 'OFFLINE' | 'TESTING'
export type PredictionStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
export type NewsType = 'MODEL_UPDATE' | 'SYSTEM_NOTICE' | 'OPERATION'

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
  modelType: 'NUMERIC'
  modelVersion: string
  modelStatus: ModelStatus
  description: string
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
