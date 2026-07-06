package com.example.pvplatform.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.pvplatform.persistence.entity.PvDataDO;
import com.example.pvplatform.module.pvdata.vo.PvDataVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PvDataMapper extends BaseMapper<PvDataDO> {
    List<PvDataVO> selectHistoryAggregated(@Param("stationId") Long stationId,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime,
                                           @Param("intervalMinutes") int intervalMinutes);

    int batchInsert(@Param("rows") List<PvDataDO> rows);

    int batchUpsert(@Param("rows") List<PvDataDO> rows);
}
