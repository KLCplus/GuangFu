package com.example.pvplatform.module.pvdata.service;

import com.example.pvplatform.module.pvdata.entity.PvData;
import com.example.pvplatform.module.pvdata.vo.PvDataVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class PvDataService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PvData realtime(Long stationId) {
        return new PvData(stationId, LocalDateTime.now().format(FORMATTER), 523.5, 380.2,
            120.6, 850.0, 31.5, 62.0, 2.5);
    }

    public List<PvDataVO> history() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(29);
        return IntStream.range(0, 30)
            .mapToObj(i -> new PvDataVO(start.plusMinutes(i).format(FORMATTER),
                500.0 + i * 0.8, 810.0 + i, 30.5 + i * 0.02))
            .toList();
    }
}
