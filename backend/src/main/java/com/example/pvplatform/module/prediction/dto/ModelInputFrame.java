package com.example.pvplatform.module.prediction.dto;

import java.time.LocalDateTime;

public record ModelInputFrame(
    LocalDateTime time,
    double power,
    double temperature,
    double irradiance
) {}
