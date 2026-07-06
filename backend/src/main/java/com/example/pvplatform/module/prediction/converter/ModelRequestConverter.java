package com.example.pvplatform.module.prediction.converter;

import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.module.prediction.dto.ModelInputFrame;

import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ModelRequestConverter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ModelRequestConverter() {}

    public static ModelPredictRequest toPredictRequest(String serviceModelName, List<ModelInputFrame> frames) {
        List<ModelPredictRequest.InputFrame> inputFrames = frames.stream()
                .map(f -> new ModelPredictRequest.InputFrame(
                        f.time().format(FORMATTER),
                        f.power(),
                        f.temperature(),
                        f.irradiance()))
                .toList();
        return new ModelPredictRequest(serviceModelName, inputFrames);
    }
}
