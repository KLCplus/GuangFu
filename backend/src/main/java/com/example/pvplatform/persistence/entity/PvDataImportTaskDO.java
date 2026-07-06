package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pv_data_import_task")
public class PvDataImportTaskDO {
    @TableId(type = IdType.AUTO)
    private Long importId;
    private Long userId;
    private Long stationId;
    private String fileName;
    private String fileUrl;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
