package com.example.pvplatform.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.pvplatform.persistence.entity.ExternalPvStationStatusDO;
import org.apache.ibatis.annotations.Param;

public interface ExternalPvStationStatusMapper extends BaseMapper<ExternalPvStationStatusDO> {
    int upsert(@Param("status") ExternalPvStationStatusDO status);
}
