package com.example.pvplatform.client.dto;

import java.util.List;

public record ModelPredictRequest(String modelName, List<InputFrame> input) {
    public record InputFrame(String time, double power, double temperature, double irradiance) {}
}
