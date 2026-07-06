package com.example.pvplatform.module.pvdata.parser;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.pvdata.dto.PvDataImportRow;
import com.example.pvplatform.module.pvdata.vo.PvDataImportErrorVO;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class CsvPvDataFileParser extends AbstractPvDataFileParser implements PvDataFileParser {
    @Override
    public boolean supports(String extension) {
        return "csv".equals(extension);
    }

    @Override
    public ParsedPvDataFile parse(InputStream input, int maxRows) throws IOException {
        List<PvDataImportRow> rows = new ArrayList<>();
        List<PvDataImportErrorVO> errors = new ArrayList<>();
        Set<java.time.LocalDateTime> times = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BusinessException(400, "文件不能为空");
            }
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            if (!parseCsvLine(headerLine).equals(HEADERS)) {
                throw new BusinessException(400, "CSV 表头不符合模板");
            }
            String line;
            int fileRow = 1;
            int total = 0;
            while ((line = reader.readLine()) != null) {
                fileRow++;
                if (line.isBlank()) {
                    continue;
                }
                if (++total > maxRows) {
                    throw new BusinessException(400, "文件数据行数超过限制 " + maxRows);
                }
                try {
                    PvDataImportRow row = toRow(fileRow, parseCsvLine(line));
                    if (!times.add(row.collectTime())) {
                        throw new IllegalArgumentException("文件内 collectTime 重复");
                    }
                    rows.add(row);
                } catch (IllegalArgumentException exception) {
                    addError(errors, fileRow, exception.getMessage());
                }
            }
            return new ParsedPvDataFile(total, rows, errors);
        }
    }

    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                fields.add(value.toString().trim());
                value.setLength(0);
            } else {
                value.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("CSV 引号未闭合");
        }
        fields.add(value.toString().trim());
        return fields;
    }

    private void addError(List<PvDataImportErrorVO> errors, int row, String message) {
        if (errors.size() < 100) {
            errors.add(new PvDataImportErrorVO(row, message));
        }
    }
}
