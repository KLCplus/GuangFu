package com.example.pvplatform.module.prediction.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.prediction.dto.ModelInputFrame;
import com.example.pvplatform.module.pvdata.service.PvDataService;
import com.example.pvplatform.module.pvdata.vo.PvDataVO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PredictionInputService {

    private final PvDataService pvDataService;

    public PredictionInputService(PvDataService pvDataService) {
        this.pvDataService = pvDataService;
    }

    /**
     * 加载 STATION_HISTORY 模式的输入数据。
     * 规则：恰好 30 帧，升序，每帧间隔 60 秒，最后一帧不能过旧，
     * 功率/温度/辐照度非空，非 NaN/Infinity，功率/辐照度非负。
     */
    public List<ModelInputFrame> loadStationHistory(Long stationId) {
        List<PvDataVO> rows = pvDataService.latestThirty(stationId);
        // latestThirty 已保证 30 条升序返回

        List<ModelInputFrame> frames = rows.stream()
                .map(r -> new ModelInputFrame(
                        parseTime(r.time()),
                        r.power(),
                        r.temperature(),
                        r.irradiance()))
                .toList();

        validateFrames(frames);
        return frames;
    }

    private void validateFrames(List<ModelInputFrame> frames) {
        if (frames.size() != 30) {
            throw new BusinessException(400, "预测需要连续 30 分钟数据");
        }

        for (int i = 0; i < frames.size(); i++) {
            ModelInputFrame f = frames.get(i);

            // 非 NaN/Infinity
            if (Double.isNaN(f.power()) || Double.isInfinite(f.power())) {
                throw new BusinessException(400, "预测输入功率值非法");
            }
            if (Double.isNaN(f.temperature()) || Double.isInfinite(f.temperature())) {
                throw new BusinessException(400, "预测输入温度值非法");
            }
            if (Double.isNaN(f.irradiance()) || Double.isInfinite(f.irradiance())) {
                throw new BusinessException(400, "预测输入辐照度值非法");
            }
            // 功率、辐照度非负
            if (f.power() < 0) {
                throw new BusinessException(400, "预测输入功率不能为负");
            }
            if (f.irradiance() < 0) {
                throw new BusinessException(400, "预测输入辐照度不能为负");
            }

            // 检查连续性和间隔
            if (i > 0) {
                ModelInputFrame prev = frames.get(i - 1);
                long gapSeconds = Duration.between(prev.time(), f.time()).getSeconds();
                if (gapSeconds != 60) {
                    throw new BusinessException(400, "预测输入数据时间不连续");
                }
                // 升序
                if (!f.time().isAfter(prev.time())) {
                    throw new BusinessException(400, "预测输入数据时间未升序");
                }
            }
        }

        // 最后一帧不能过旧（超过 10 分钟视为过旧）
        ModelInputFrame last = frames.get(frames.size() - 1);
        if (Duration.between(last.time(), LocalDateTime.now()).toMinutes() > 10) {
            throw new BusinessException(400, "预测输入数据过旧，最后一帧时间: " + last.time());
        }
    }

    private LocalDateTime parseTime(String time) {
        try {
            return LocalDateTime.parse(time, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            throw new BusinessException(400, "输入数据时间格式错误");
        }
    }
}
