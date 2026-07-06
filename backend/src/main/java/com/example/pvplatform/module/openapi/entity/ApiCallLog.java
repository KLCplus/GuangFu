package com.example.pvplatform.module.openapi.entity;

public record ApiCallLog(Long logId, String modelName, String requestTime, long costTime, String status) {}
