package com.example.pvplatform.module.analysis.service;

import com.example.pvplatform.module.analysis.dto.AnalysisRequest;
import com.example.pvplatform.module.analysis.entity.AnalysisReport;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AnalysisService {
    public AnalysisReport report(AnalysisRequest request) {
        return new AnalysisReport("成都示范光伏电站", LocalDateTime.now().toString(),
            "当前电站运行状态良好，近 30 分钟发电功率整体稳定。",
            "当前天气晴朗，辐照条件较好，有利于光伏发电。",
            "模型预测显示未来 30 分钟功率整体呈小幅上升趋势。",
            "建议继续监测，并关注天气变化对发电功率的影响。");
    }
}
