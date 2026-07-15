const MODEL_ICON_MAP = Object.freeze({
  DLinear: '/assets/model-icons/dlinear.png',
  PatchTST: '/assets/model-icons/patchtst.png',
  iTransformer: '/assets/model-icons/itransformer.png',
  TimeXer: '/assets/model-icons/timexer.png',
  TimeMixer: '/assets/model-icons/timemixer.png',
  TSMixer: '/assets/model-icons/tsmixer.png',
  Transformer: '/assets/model-icons/transformer.png',
  CNN_LSTM: '/assets/model-icons/cnn-lstm.png',
  CNN_MLP: '/assets/model-icons/cnn-mlp.png',
  '3DCNN_LSTM': '/assets/model-icons/3dcnn-lstm.png',
  ConvLSTM_LSTM: '/assets/model-icons/convlstm-lstm.png',
  SimVP_gSTA: '/assets/model-icons/simvp-gsta.png',
  TAU: '/assets/model-icons/tau.png',
  ConvLSTM: '/assets/model-icons/convlstm.png',
  PredRNN: '/assets/model-icons/predrnn.png',
  'PredRNN++': '/assets/model-icons/predrnnpp.png',
  E3D_LSTM: '/assets/model-icons/e3d-lstm.png',
  swinLSTM: '/assets/model-icons/swin-lstm.png',
  SUNSET: '/assets/model-icons/sunset.png'
})

const FALLBACK_MODEL_ICON = '/assets/model-icons/fallback.png'

function getModelIcon(modelCode) {
  return MODEL_ICON_MAP[String(modelCode || '').trim()] || FALLBACK_MODEL_ICON
}

module.exports = { MODEL_ICON_MAP, FALLBACK_MODEL_ICON, getModelIcon }
