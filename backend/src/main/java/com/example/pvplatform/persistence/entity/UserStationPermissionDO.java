package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_station_permission")
public class UserStationPermissionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long stationId;
    private String permissionType;
    private LocalDateTime createdAt;
}
