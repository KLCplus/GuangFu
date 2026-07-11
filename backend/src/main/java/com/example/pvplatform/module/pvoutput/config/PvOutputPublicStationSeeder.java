package com.example.pvplatform.module.pvoutput.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.persistence.entity.ExternalPvStationDO;
import com.example.pvplatform.persistence.mapper.ExternalPvStationMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Order(5)
public class PvOutputPublicStationSeeder implements ApplicationRunner {
    private final ExternalPvStationMapper stationMapper;

    public PvOutputPublicStationSeeder(ExternalPvStationMapper stationMapper) {
        this.stationMapper = stationMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long existing = stationMapper.selectCount(Wrappers.<ExternalPvStationDO>lambdaQuery()
            .eq(ExternalPvStationDO::getSource, "PVOUTPUT"));
        if (existing != null && existing > 0) {
            return;
        }
        defaultStations().forEach(stationMapper::insert);
    }

    private List<ExternalPvStationDO> defaultStations() {
        return List.of(
            station(85414L, "RPM Building 37", 326250, "4110", "North", "725x450W LG Neon H", "SolarEdge SE82.8K", "Australia"),
            station(81697L, "Woodrose", 40716, "2446", "North", "108x377W Jinko", "SMA & AlphaESS", "Australia"),
            station(71111L, "Arcare Parkwood 100kw LG neon2", 100000, "4214", "North", "250x400W LG Neon2", "Fronius ECO 25", "Australia"),
            station(84975L, "Pro Tech Distributions Unit 1", 96360, "4132", "North", "264x365W Jinko JKM365N-6TL3", "Fronius Eco 27.0-3-S", "Australia"),
            station(75956L, "HISA3", 100800, "Japan", "South West", "360x280W JKM280PP-60-J", "SPSS-55D", "Japan")
        );
    }

    private ExternalPvStationDO station(Long systemId, String name, Integer sizeW, String postcode, String orientation,
                                        String panel, String inverter, String location) {
        ExternalPvStationDO station = new ExternalPvStationDO();
        station.setSource("PVOUTPUT");
        station.setExternalSystemId(systemId);
        station.setSystemName(name);
        station.setSystemSizeW(sizeW);
        station.setPostcode(postcode);
        station.setOrientation(orientation);
        station.setPanel(panel);
        station.setInverter(inverter);
        station.setLastOutputText(location);
        station.setDistanceKm(BigDecimal.ZERO);
        station.setEnabled(true);
        return station;
    }
}
