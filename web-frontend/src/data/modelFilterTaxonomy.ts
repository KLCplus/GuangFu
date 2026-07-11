import type { ModelListItem } from '../api/model'

export type ArchitectureFilter =
  | '线性 / 分解'
  | 'Transformer'
  | 'MLP / Mixer'
  | 'CNN'
  | 'CNN + LSTM'
  | 'ConvLSTM'
  | '时空递归网络'
  | '视频预测网络'

export type CapabilityFilter =
  | '数值预测'
  | '多步预测'
  | '外生变量'
  | '多尺度建模'
  | '图像数值融合'
  | '天空图像'
  | '时空建模'
  | '注意力机制'

interface ModelFilterClassification {
  architectures: ArchitectureFilter[]
  capabilities: CapabilityFilter[]
}

/**
 * 当前 19 个模型的规范化筛选分类。
 * 映射依据 model_marketplace_metadata_19_models.sql 中的 model_code、model_family、tags 与 capabilities；
 * 这里只改变筛选入口，不覆盖或删减接口返回的原始元数据。
 */
export const MODEL_FILTER_CLASSIFICATION: Record<string, ModelFilterClassification> = {
  DLinear: {
    architectures: ['线性 / 分解'],
    capabilities: ['数值预测', '多步预测']
  },
  PatchTST: {
    architectures: ['Transformer'],
    capabilities: ['数值预测', '多步预测', '注意力机制']
  },
  iTransformer: {
    architectures: ['Transformer'],
    capabilities: ['数值预测', '多步预测', '注意力机制']
  },
  TimeXer: {
    architectures: ['Transformer'],
    capabilities: ['数值预测', '多步预测', '外生变量', '注意力机制']
  },
  TimeMixer: {
    architectures: ['线性 / 分解', 'MLP / Mixer'],
    capabilities: ['数值预测', '多步预测', '多尺度建模']
  },
  TSMixer: {
    architectures: ['MLP / Mixer'],
    capabilities: ['数值预测', '多步预测']
  },
  Transformer: {
    architectures: ['Transformer'],
    capabilities: ['数值预测', '多步预测', '注意力机制']
  },
  CNN_MLP: {
    architectures: ['CNN', 'MLP / Mixer'],
    capabilities: ['图像数值融合', '天空图像']
  },
  CNN_LSTM: {
    architectures: ['CNN', 'CNN + LSTM'],
    capabilities: ['图像数值融合', '天空图像', '时空建模']
  },
  '3DCNN_LSTM': {
    architectures: ['CNN', 'CNN + LSTM'],
    capabilities: ['图像数值融合', '天空图像', '时空建模']
  },
  ConvLSTM_LSTM: {
    architectures: ['CNN + LSTM', 'ConvLSTM'],
    capabilities: ['图像数值融合', '天空图像', '时空建模']
  },
  SimVP_gSTA: {
    architectures: ['视频预测网络'],
    capabilities: ['时空建模', '注意力机制']
  },
  TAU: {
    architectures: ['视频预测网络'],
    capabilities: ['时空建模', '注意力机制']
  },
  ConvLSTM: {
    architectures: ['ConvLSTM', '时空递归网络', '视频预测网络'],
    capabilities: ['时空建模']
  },
  PredRNN: {
    architectures: ['时空递归网络', '视频预测网络'],
    capabilities: ['时空建模']
  },
  'PredRNN++': {
    architectures: ['时空递归网络', '视频预测网络'],
    capabilities: ['时空建模']
  },
  E3D_LSTM: {
    architectures: ['时空递归网络', '视频预测网络'],
    capabilities: ['时空建模']
  },
  swinLSTM: {
    architectures: ['Transformer', '时空递归网络', '视频预测网络'],
    capabilities: ['时空建模', '注意力机制']
  },
  SUNSET: {
    architectures: ['CNN'],
    capabilities: ['图像数值融合', '天空图像']
  }
}

const TAG_CAPABILITY_ALIASES: Record<string, CapabilityFilter> = {
  数值预测: '数值预测',
  多步预测: '多步预测',
  多步功率预测: '多步预测',
  外生变量: '外生变量',
  多尺度: '多尺度建模',
  多尺度建模: '多尺度建模',
  图像数值融合: '图像数值融合',
  天空图像: '天空图像',
  时空建模: '时空建模',
  时空预测: '时空建模',
  时间注意力: '注意力机制',
  自注意力: '注意力机制',
  注意力机制: '注意力机制'
}

const FAMILY_ARCHITECTURE_RULES: Array<{ pattern: RegExp; value: ArchitectureFilter }> = [
  { pattern: /linear|decomposition/i, value: '线性 / 分解' },
  { pattern: /transformer|attention/i, value: 'Transformer' },
  { pattern: /mlp|mixer/i, value: 'MLP / Mixer' },
  { pattern: /cnn/i, value: 'CNN' },
  { pattern: /cnn.*lstm|lstm.*cnn/i, value: 'CNN + LSTM' },
  { pattern: /convlstm/i, value: 'ConvLSTM' },
  { pattern: /spatiotemporal.*rnn|spatiotemporal.*lstm|causal.*rnn|eidetic/i, value: '时空递归网络' }
]

export function getModelArchitectures(model: ModelListItem): ArchitectureFilter[] {
  const values = [...(MODEL_FILTER_CLASSIFICATION[model.modelCode]?.architectures ?? [])]
  const family = model.modelFamily?.trim() ?? ''
  FAMILY_ARCHITECTURE_RULES.forEach((rule) => {
    if (family && rule.pattern.test(family)) values.push(rule.value)
  })
  return unique(values)
}

export function getModelCapabilities(model: ModelListItem): CapabilityFilter[] {
  const values = [...(MODEL_FILTER_CLASSIFICATION[model.modelCode]?.capabilities ?? [])]
  for (const tag of model.tags ?? []) {
    const normalized = TAG_CAPABILITY_ALIASES[tag.trim()]
    if (normalized) values.push(normalized)
  }
  return unique(values)
}

function unique<T extends string>(values: T[]): T[] {
  return Array.from(new Set(values))
}
