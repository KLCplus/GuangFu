package com.example.pvplatform.module.station.entity;

public record PowerStation(
    Long stationId, String stationName, String province, String city, String address,
    Double longitude, Double latitude, Double capacity, String status, String description
) {}
