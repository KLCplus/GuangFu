package com.example.pvplatform.module.pvdata.vo;

public record PvDataVO(
    String time,
    Double power,
    Double voltage,
    Double current,
    Double irradiance,
    Double temperature,
    Double humidity,
    Double windSpeed
) {}
