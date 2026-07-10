package com.example.pvplatform.module.pvoutput.util;

import com.example.pvplatform.module.pvoutput.dto.PvOutputStationSearchResultDTO;
import com.example.pvplatform.persistence.entity.ExternalPvStationStatusDO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class PvOutputCsvParser {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public List<PvOutputStationSearchResultDTO> parseStations(String payload) {
        List<PvOutputStationSearchResultDTO> results = new ArrayList<>();
        for (String line : lines(payload)) {
            List<String> f = parseLine(line);
            Long externalSystemId = longValue(field(f, 6));
            if (externalSystemId == null) {
                continue;
            }
            results.add(new PvOutputStationSearchResultDTO(
                text(field(f, 0)), intValue(field(f, 1)), text(field(f, 2)), text(field(f, 3)),
                intValue(field(f, 4)), text(field(f, 5)), externalSystemId, text(field(f, 7)),
                text(field(f, 8)), decimal(field(f, 9)), decimal(field(f, 10)), decimal(field(f, 11))
            ));
        }
        return results;
    }

    public ExternalPvStationStatusDO parseStatus(String payload, Long externalSystemId) {
        String line = lines(payload).stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("PVOutput 未返回状态数据"));
        List<String> f = parseLine(line);
        LocalDate date = date(field(f, 0));
        LocalTime time = time(field(f, 1));
        if (date == null || time == null) {
            throw new IllegalArgumentException("PVOutput 状态时间字段缺失");
        }
        ExternalPvStationStatusDO status = new ExternalPvStationStatusDO();
        status.setExternalSystemId(externalSystemId);
        status.setSampleTime(LocalDateTime.of(date, time));
        status.setEnergyGenerationWh(intValue(field(f, 2)));
        status.setPowerGenerationW(intValue(field(f, 3)));
        status.setEnergyConsumptionWh(intValue(field(f, 4)));
        status.setPowerConsumptionW(intValue(field(f, 5)));
        status.setNormalisedOutput(decimal(field(f, 6)));
        status.setTemperatureC(decimal(field(f, 7)));
        status.setVoltageV(decimal(field(f, 8)));
        status.setRawPayload(payload);
        return status;
    }

    private List<String> lines(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        return payload.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    }

    private List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(c);
            }
        }
        values.add(value.toString());
        return values;
    }

    private String field(List<String> fields, int index) {
        return index < fields.size() ? fields.get(index) : null;
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "NaN".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private Integer intValue(String value) {
        BigDecimal decimal = decimal(value);
        return decimal == null ? null : decimal.intValue();
    }

    private Long longValue(String value) {
        BigDecimal decimal = decimal(value);
        return decimal == null ? null : decimal.longValue();
    }

    private BigDecimal decimal(String value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate date(String value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text, DATE);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LocalTime time(String value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalTime.parse(text, TIME);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
