package com.example.pvplatform.module.station.dto;

public record StationRequest(
    String stationName, String province, String city, String address,
    Double longitude, Double latitude, Double capacity, String status, String description
) {}
