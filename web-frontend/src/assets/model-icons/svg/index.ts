import dlinear from './dlinear.svg';
import patchtst from './patchtst.svg';
import itransformer from './itransformer.svg';
import timexer from './timexer.svg';
import timemixer from './timemixer.svg';
import tsmixer from './tsmixer.svg';
import transformer from './transformer.svg';
import cnnLstm from './cnn-lstm.svg';
import cnnMlp from './cnn-mlp.svg';
import threeDcnnLstm from './3dcnn-lstm.svg';
import convlstmLstm from './convlstm-lstm.svg';
import simvpGsta from './simvp-gsta.svg';
import tau from './tau.svg';
import convlstm from './convlstm.svg';
import predrnn from './predrnn.svg';
import predrnnpp from './predrnnpp.svg';
import e3dLstm from './e3d-lstm.svg';
import swinLstm from './swin-lstm.svg';
import sunset from './sunset.svg';
import genericNumeric from './generic-numeric.svg';
import genericFusion from './generic-fusion.svg';
import genericMultimodal from './generic-multimodal.svg';

export const modelIconMap: Record<string, string> = {
  DLinear: dlinear,
  PatchTST: patchtst,
  iTransformer: itransformer,
  TimeXer: timexer,
  TimeMixer: timemixer,
  TSMixer: tsmixer,
  Transformer: transformer,
  CNN_LSTM: cnnLstm,
  CNN_MLP: cnnMlp,
  '3DCNN_LSTM': threeDcnnLstm,
  ConvLSTM_LSTM: convlstmLstm,
  SimVP_gSTA: simvpGsta,
  TAU: tau,
  ConvLSTM: convlstm,
  PredRNN: predrnn,
  'PredRNN++': predrnnpp,
  E3D_LSTM: e3dLstm,
  swinLSTM: swinLstm,
  SUNSET: sunset,
};

export const typeFallbackIconMap: Record<string, string> = {
  NUMERIC: genericNumeric,
  FUSION: genericFusion,
  MULTIMODAL: genericMultimodal,
  IMAGE_TO_NUMERIC: genericFusion,
};

export function getModelIcon(modelCode?: string | null, modelType?: string | null): string {
  const byCode = modelCode ? modelIconMap[modelCode] : undefined;
  if (byCode) return byCode;
  const byType = modelType ? typeFallbackIconMap[modelType] : undefined;
  return byType || genericNumeric;
}
