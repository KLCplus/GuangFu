package com.example.pvplatform.module.pvoutput.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.module.pvoutput.client.PvOutputClient;
import com.example.pvplatform.module.pvoutput.dto.PvOutputStatusDTO;
import com.example.pvplatform.module.pvoutput.dto.PvOutputSyncResultDTO;
import com.example.pvplatform.persistence.entity.ExternalPvStationDO;
import com.example.pvplatform.persistence.entity.ExternalPvStationStatusDO;
import com.example.pvplatform.persistence.mapper.ExternalPvStationMapper;
import com.example.pvplatform.persistence.mapper.ExternalPvStationStatusMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PvOutputLivePageSyncService {
    private static final Pattern ROW = Pattern.compile("<tr class='[eo]2'>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SID = Pattern.compile("display\\.jsp\\?sid=(\\d+)");
    private static final Pattern NAME = Pattern.compile("intraday\\.jsp\\?id=\\d+&sid=\\d+'>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SYSTEM_TITLE = Pattern.compile("class='system' title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final PvOutputClient client;
    private final ExternalPvStationMapper stationMapper;
    private final ExternalPvStationStatusMapper statusMapper;

    public PvOutputLivePageSyncService(PvOutputClient client,
                                       ExternalPvStationMapper stationMapper,
                                       ExternalPvStationStatusMapper statusMapper) {
        this.client = client;
        this.stationMapper = stationMapper;
        this.statusMapper = statusMapper;
    }

    public List<PvOutputSyncResultDTO> syncLiveOutputs(int limit) {
        String html = client.getLiveOutputsPage();
        List<LiveRow> rows = parseRows(html, limit <= 0 ? 30 : limit);
        List<PvOutputSyncResultDTO> results = new ArrayList<>();
        LocalDateTime fetchedAt = LocalDateTime.now().withSecond(0).withNano(0);
        for (LiveRow row : rows) {
            ExternalPvStationDO station = upsertStation(row, fetchedAt);
            ExternalPvStationStatusDO status = upsertStatus(row, fetchedAt);
            results.add(new PvOutputSyncResultDTO(station.getId(), row.systemId(), row.name(), "SUCCESS",
                "公开实时页同步成功", toStatusDTO(status)));
        }
        return results;
    }

    private ExternalPvStationDO upsertStation(LiveRow row, LocalDateTime fetchedAt) {
        ExternalPvStationDO station = stationMapper.selectOne(Wrappers.<ExternalPvStationDO>lambdaQuery()
            .eq(ExternalPvStationDO::getExternalSystemId, row.systemId())
            .last("LIMIT 1"));
        if (station == null) {
            station = new ExternalPvStationDO();
            station.setSource("PVOUTPUT");
            station.setExternalSystemId(row.systemId());
            station.setEnabled(true);
        }
        station.setSystemName(row.name());
        station.setSystemSizeW(row.systemSizeW());
        station.setPostcode(row.location());
        station.setPanel(row.panel());
        station.setInverter(row.inverter());
        station.setOrientation(row.orientation());
        station.setLastOutputText("live.jsp");
        station.setLastSyncTime(fetchedAt);
        station.setLastSyncStatus("SUCCESS");
        station.setLastSyncError(null);
        // 公开实时页不提供经纬度: 新插入的电站经纬度为 NULL, 更新时保留已有坐标
        // MyBatis-Plus 默认 NOT_NULL 策略: null 字段不会写入 UPDATE 语句, 已有经纬度不会被覆盖
        if (station.getId() == null) {
            stationMapper.insert(station);
            return stationMapper.selectOne(Wrappers.<ExternalPvStationDO>lambdaQuery()
                .eq(ExternalPvStationDO::getExternalSystemId, row.systemId())
                .last("LIMIT 1"));
        }
        stationMapper.updateById(station);
        return station;
    }

    private ExternalPvStationStatusDO upsertStatus(LiveRow row, LocalDateTime fetchedAt) {
        ExternalPvStationStatusDO status = new ExternalPvStationStatusDO();
        status.setExternalSystemId(row.systemId());
        status.setSampleTime(fetchedAt);
        status.setEnergyGenerationWh(row.energyWh());
        status.setPowerGenerationW(row.powerW());
        status.setNormalisedOutput(row.efficiencyKwhPerKw());
        status.setRawPayload(row.rawText());
        statusMapper.upsert(status);
        return statusMapper.selectOne(Wrappers.<ExternalPvStationStatusDO>lambdaQuery()
            .eq(ExternalPvStationStatusDO::getExternalSystemId, row.systemId())
            .eq(ExternalPvStationStatusDO::getSampleTime, fetchedAt)
            .last("LIMIT 1"));
    }

    private List<LiveRow> parseRows(String html, int limit) {
        List<LiveRow> rows = new ArrayList<>();
        Matcher matcher = ROW.matcher(html == null ? "" : html);
        while (matcher.find() && rows.size() < limit) {
            LiveRow row = parseRow(matcher.group(1));
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private LiveRow parseRow(String rowHtml) {
        Long systemId = longMatch(SID, rowHtml);
        if (systemId == null) {
            return null;
        }
        String name = htmlText(firstMatch(NAME, rowHtml));
        String title = firstMatch(SYSTEM_TITLE, rowHtml);
        String text = htmlText(rowHtml).replace('\u00a0', ' ').trim();
        List<String> cells = tableCells(rowHtml);
        String location = cells.size() > 2 ? htmlText(cells.get(2)).replaceFirst("^[A-Za-z]+\\s+", "").trim() : null;
        Integer sizeW = cells.size() > 3 ? wattsFromText(htmlText(cells.get(3))) : null;
        Integer energyWh = cells.size() > 4 ? wattHoursFromKwhText(htmlText(cells.get(4))) : null;
        BigDecimal efficiency = cells.size() > 5 ? decimalFromText(htmlText(cells.get(5))) : null;
        Integer powerW = cells.size() > 6 ? wattsFromText(htmlText(cells.get(6))) : null;
        return new LiveRow(systemId, blankToNull(name), blankToNull(location), sizeW, energyWh, efficiency, powerW,
            detail(title, "Panels:"), detail(title, "Inverter:"), orientation(title), text);
    }

    private List<String> tableCells(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Matcher matcher = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(rowHtml);
        while (matcher.find()) {
            cells.add(matcher.group(1));
        }
        return cells;
    }

    private String detail(String title, String label) {
        if (title == null) return null;
        int start = title.indexOf(label);
        if (start < 0) return null;
        start += label.length();
        int end = title.indexOf("<br/>", start);
        return htmlText(end < 0 ? title.substring(start) : title.substring(start, end)).trim();
    }

    private String orientation(String title) {
        String value = detail(title, "Orientation:");
        if (value == null) return null;
        int shadeIndex = value.indexOf(" - ");
        return shadeIndex < 0 ? value : value.substring(0, shadeIndex).trim();
    }

    private String htmlText(String value) {
        if (value == null) return null;
        return value.replaceAll("<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&deg;", " degrees")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Long longMatch(Pattern pattern, String text) {
        String value = firstMatch(pattern, text);
        return value == null ? null : Long.valueOf(value);
    }

    private Integer wattsFromText(String text) {
        BigDecimal number = decimalFromText(text);
        if (number == null) return null;
        if (text != null && text.toLowerCase().contains("kw")) {
            number = number.multiply(BigDecimal.valueOf(1000));
        }
        return number.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private Integer wattHoursFromKwhText(String text) {
        BigDecimal number = decimalFromText(text);
        return number == null ? null : number.multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private BigDecimal decimalFromText(String text) {
        if (text == null) return null;
        Matcher matcher = Pattern.compile("([0-9][0-9,]*\\.?[0-9]*)").matcher(text);
        if (!matcher.find()) return null;
        return new BigDecimal(matcher.group(1).replace(",", ""));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private PvOutputStatusDTO toStatusDTO(ExternalPvStationStatusDO status) {
        return new PvOutputStatusDTO(status.getId(), status.getExternalSystemId(), status.getSampleTime(),
            status.getEnergyGenerationWh(), status.getPowerGenerationW(), status.getEnergyConsumptionWh(),
            status.getPowerConsumptionW(), status.getNormalisedOutput(), status.getTemperatureC(),
            status.getVoltageV(), status.getFetchedAt());
    }

    private record LiveRow(Long systemId, String name, String location, Integer systemSizeW, Integer energyWh,
                           BigDecimal efficiencyKwhPerKw, Integer powerW, String panel, String inverter,
                           String orientation, String rawText) {}
}
