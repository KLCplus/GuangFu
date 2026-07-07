package com.example.pvplatform.client.dto;

import java.util.List;

public record ModelPredictRequest(
        String modelName,
        List<InputFrame> input,
        List<CloudImageFrame> cloudImages
) {
    public ModelPredictRequest(String modelName, List<InputFrame> input) {
        this(modelName, input, null);
    }

    public record InputFrame(
            String time,
            double power,
            Double temperature,
            Double irradiance,
            String cloudImage,
            String cloudImageBase64
    ) {
        public InputFrame(String time, double power, double temperature, double irradiance) {
            this(time, power, temperature, irradiance, null, null);
        }

        public InputFrame(String time, double power) {
            this(time, power, null, null, null, null);
        }
    }

    public record CloudImageFrame(
            String time,
            String cloudImage,
            String cloudImageBase64,
            String source,
            String file
    ) {
        public CloudImageFrame(String time, String cloudImage) {
            this(time, cloudImage, null, null, null);
        }
    }
}
