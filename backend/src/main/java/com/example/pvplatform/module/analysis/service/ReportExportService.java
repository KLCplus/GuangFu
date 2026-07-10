package com.example.pvplatform.module.analysis.service;

import com.example.pvplatform.module.analysis.vo.AnalysisReportVO;
import org.springframework.stereotype.Service;

@Service
public class ReportExportService {
    public String markdown(AnalysisReportVO report) {
        if (report == null) {
            return "";
        }
        if (report.markdown() != null && !report.markdown().isBlank()) {
            return report.markdown();
        }
        return report.reportContent() == null ? "" : report.reportContent();
    }
}
