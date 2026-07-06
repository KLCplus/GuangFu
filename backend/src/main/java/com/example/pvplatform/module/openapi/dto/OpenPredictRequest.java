package com.example.pvplatform.module.openapi.dto;

import com.example.pvplatform.client.dto.ModelPredictRequest;

import java.util.List;

public record OpenPredictRequest(String modelName, List<ModelPredictRequest.InputFrame> input) {}
