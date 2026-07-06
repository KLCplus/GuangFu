package com.example.pvplatform.module.weather.entity;

public record WeatherData(Long stationId, String weather, double temperature, double humidity, String reportTime) {}
