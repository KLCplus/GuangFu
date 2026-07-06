package com.example.pvplatform.module.pvdata.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.pvdata.entity.PvData;
import com.example.pvplatform.module.pvdata.vo.PvDataVO;
import com.example.pvplatform.persistence.entity.PvDataDO;
import com.example.pvplatform.persistence.mapper.PvDataMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class PvDataService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PvDataMapper pvDataMapper;

    public PvDataService(PvDataMapper pvDataMapper) {
        this.pvDataMapper = pvDataMapper;
    }

    public PvData realtime(Long stationId) {
        PvDataDO data = pvDataMapper.selectOne(Wrappers.<PvDataDO>lambdaQuery()
            .eq(PvDataDO::getStationId, stationId)
            .orderByDesc(PvDataDO::getCollectTime)
            .last("LIMIT 1"));
        if (data == null) {
            throw new BusinessException(404, "该电站暂无光伏数据");
        }
        return new PvData(data.getStationId(), format(data.getCollectTime()), number(data.getPowerKw()),
            number(data.getVoltageV()), number(data.getCurrentA()), number(data.getIrradianceWM2()),
            number(data.getAmbientTemperatureC()), number(data.getHumidityPercent()), number(data.getWindSpeedMS()));
    }

    public List<PvDataVO> history(Long stationId, LocalDateTime startTime, LocalDateTime endTime) {
        var query = Wrappers.<PvDataDO>lambdaQuery()
            .eq(PvDataDO::getStationId, stationId)
            .ge(startTime != null, PvDataDO::getCollectTime, startTime)
            .le(endTime != null, PvDataDO::getCollectTime, endTime)
            .orderByAsc(PvDataDO::getCollectTime);
        return pvDataMapper.selectList(query).stream().map(this::toVO).toList();
    }

    public List<PvDataVO> latestThirty(Long stationId) {
        List<PvDataDO> rows = new ArrayList<>(pvDataMapper.selectList(Wrappers.<PvDataDO>lambdaQuery()
            .eq(PvDataDO::getStationId, stationId)
            .orderByDesc(PvDataDO::getCollectTime)
            .last("LIMIT 30")));
        Collections.reverse(rows);
        if (rows.size() < 30) {
            throw new BusinessException(400, "预测需要该电站连续 30 分钟的数据，当前仅有 " + rows.size() + " 条");
        }
        return rows.stream().map(this::toVO).toList();
    }

    private PvDataVO toVO(PvDataDO data) {
        return new PvDataVO(format(data.getCollectTime()), number(data.getPowerKw()),
            number(data.getIrradianceWM2()), number(data.getAmbientTemperatureC()));
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(FORMATTER);
    }

    private double number(BigDecimal value) {
        return value == null ? 0D : value.doubleValue();
    }
}
