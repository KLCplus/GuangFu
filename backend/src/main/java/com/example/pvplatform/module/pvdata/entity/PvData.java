package com.example.pvplatform.module.pvdata.entity;

public record PvData(
    Long stationId, String collectTime, double power, double voltage, double current,
    double irradiance, double temperature, double humidity, double windSpeed
) {}
