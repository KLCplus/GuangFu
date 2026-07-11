package com.example.pvplatform.client.dto;

import java.util.List;

public record CloudPredictRequest(
    String modelName,
    List<String> inputImages
) {}
