package com.example.pvplatform.module.pvdata.parser;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.pvdata.dto.PvDataImportRow;
import com.example.pvplatform.module.pvdata.vo.PvDataImportErrorVO;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
public class ExcelPvDataFileParser extends AbstractPvDataFileParser implements PvDataFileParser {
    @Override
    public boolean supports(String extension) {
        return "xlsx".equals(extension);
    }

    @Override
    public ParsedPvDataFile parse(InputStream input, int maxRows) throws IOException {
        ZipSecureFile.setMinInflateRatio(0.01);
        List<PvDataImportRow> rows = new ArrayList<>();
        List<PvDataImportErrorVO> errors = new ArrayList<>();
        Set<java.time.LocalDateTime> times = new HashSet<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (Workbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException(400, "XLSX 不包含工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null || !cells(header, formatter).equals(HEADERS)) {
                throw new BusinessException(400, "XLSX 表头不符合模板");
            }
            int total = 0;
            for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row excelRow = sheet.getRow(index);
                if (excelRow == null || isBlank(excelRow, formatter)) {
                    continue;
                }
                if (++total > maxRows) {
                    throw new BusinessException(400, "文件数据行数超过限制 " + maxRows);
                }
                int fileRow = index + 1;
                try {
                    rejectFormulas(excelRow);
                    PvDataImportRow row = toRow(fileRow, cells(excelRow, formatter));
                    if (!times.add(row.collectTime())) {
                        throw new IllegalArgumentException("文件内 collectTime 重复");
                    }
                    rows.add(row);
                } catch (IllegalArgumentException exception) {
                    if (errors.size() < 100) {
                        errors.add(new PvDataImportErrorVO(fileRow, exception.getMessage()));
                    }
                }
            }
            return new ParsedPvDataFile(total, rows, errors);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(400, "XLSX 文件损坏或格式不合法");
        }
    }

    private List<String> cells(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>(HEADERS.size());
        for (int i = 0; i < HEADERS.size(); i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return values;
    }

    private boolean isBlank(Row row, DataFormatter formatter) {
        return cells(row, formatter).stream().allMatch(String::isBlank);
    }

    private void rejectFormulas(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.FORMULA) {
                throw new IllegalArgumentException("不允许公式单元格");
            }
        }
    }
}
