package com.example.pvplatform.client.dto;

import java.util.List;

public record ModelPredictRequest(String modelName, List<InputFrame> input, List<ImageFrame> inputImages) {
    public record InputFrame(String time, double power, double temperature, double irradiance) {}
    public record ImageFrame(String time, String image) {}
}
