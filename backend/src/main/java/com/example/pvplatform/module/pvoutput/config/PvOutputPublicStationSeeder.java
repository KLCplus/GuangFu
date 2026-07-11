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
            station(85414L, "RPM Building 37", 326250, "4110", "North",
                "725x450W LG Neon H", "SolarEdge SE82.8K", "Australia",
                new BigDecimal("-27.614000"), new BigDecimal("152.973000")),
            station(81697L, "Woodrose", 40716, "2446", "North",
                "108x377W Jinko", "SMA & AlphaESS", "Australia",
                new BigDecimal("-31.460000"), new BigDecimal("152.730000")),
            station(71111L, "Arcare Parkwood 100kw LG neon2", 100000, "4214", "North",
                "250x400W LG Neon2", "Fronius ECO 25", "Australia",
                new BigDecimal("-27.960000"), new BigDecimal("153.370000")),
            station(84975L, "Pro Tech Distributions Unit 1", 96360, "4132", "North",
                "264x365W Jinko JKM365N-6TL3", "Fronius Eco 27.0-3-S", "Australia",
                new BigDecimal("-27.680000"), new BigDecimal("153.100000")),
            station(75956L, "HISA3", 100800, "Japan", "South West",
                "360x280W JKM280PP-60-J", "SPSS-55D", "Japan",
                new BigDecimal("35.680000"), new BigDecimal("139.760000"))
        );
    }

    private ExternalPvStationDO station(Long systemId, String name, Integer sizeW, String postcode,
                                        String orientation, String panel, String inverter, String location,
                                        BigDecimal latitude, BigDecimal longitude) {
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
        station.setLatitude(latitude);
        station.setLongitude(longitude);
        station.setEnabled(true);
        return station;
    }
}
