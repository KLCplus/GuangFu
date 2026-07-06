package com.example.pvplatform.module.pvdata.parser;

import com.example.pvplatform.module.pvdata.dto.PvDataImportRow;
import com.example.pvplatform.module.pvdata.vo.PvDataImportErrorVO;

import java.util.List;

public record ParsedPvDataFile(
    int totalCount,
    List<PvDataImportRow> rows,
    List<PvDataImportErrorVO> errors
) {
}
