package com.example.pvplatform.module.station.service;

import com.example.pvplatform.module.station.dto.StationRequest;
import com.example.pvplatform.module.station.entity.PowerStation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class StationService {
    private final AtomicLong idGenerator = new AtomicLong(2);

    public List<PowerStation> list() {
        return List.of(
            new PowerStation(1L, "成都示范光伏电站", "四川省", "成都市", "高新区示范园",
                104.0668, 30.5728, 1200.0, "RUNNING", "骨架版本示例电站"),
            new PowerStation(2L, "德阳分布式光伏电站", "四川省", "德阳市", "旌阳区",
                104.3979, 31.1270, 800.0, "RUNNING", "骨架版本示例电站")
        );
    }

    public PowerStation detail(Long stationId) {
        return list().stream().filter(item -> item.stationId().equals(stationId)).findFirst()
            .orElse(new PowerStation(stationId, "示例电站", "四川省", "成都市", "待补充",
                104.06, 30.57, 1000.0, "RUNNING", "mock 数据"));
    }

    public Long create(StationRequest request) {
        return idGenerator.incrementAndGet();
    }
}
