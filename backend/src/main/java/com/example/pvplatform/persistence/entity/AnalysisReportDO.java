package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("analysis_report")
public class AnalysisReportDO {
    @TableId(type = IdType.AUTO)
    private Long reportId;
    private Long userId;
    private Long stationId;
    private Long taskId;
    private String title;
    private String summary;
    private String weatherAnalysis;
    private String predictionAnalysis;
    private String abnormalAnalysis;
    private String suggestion;
    private String reportContent;
    private String reportJson;
    private LocalDateTime createdAt;
}
