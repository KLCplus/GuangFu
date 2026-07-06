package com.example.pvplatform.module.pvdata.parser;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PvDataFileParserTest {
    private static final String HEADER =
        "collectTime,powerKw,energyTodayKwh,energyTotalKwh,voltageV,currentA,"
            + "irradianceWM2,moduleTemperatureC,ambientTemperatureC,humidityPercent,windSpeedMS";

    @Test
    void shouldParseCsvAndReportInvalidRows() throws Exception {
        String csv = HEADER + "\n"
            + "2026-07-06 10:00:00,10,,,,,800,,30,60,2\n"
            + "bad-time,20,,,,,900,,31,61,3\n";
        ParsedPvDataFile result = new CsvPvDataFileParser().parse(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), 10);
        assertEquals(2, result.totalCount());
        assertEquals(1, result.rows().size());
        assertEquals(1, result.errors().size());
    }

    @Test
    void shouldParseXlsxAndRejectFormula() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            for (int i = 0; i < AbstractPvDataFileParser.HEADERS.size(); i++) {
                header.createCell(i).setCellValue(AbstractPvDataFileParser.HEADERS.get(i));
            }
            var valid = sheet.createRow(1);
            valid.createCell(0).setCellValue("2026-07-06 10:00:00");
            valid.createCell(1).setCellValue("10");
            var invalid = sheet.createRow(2);
            invalid.createCell(0).setCellValue("2026-07-06 10:01:00");
            invalid.createCell(1).setCellFormula("1+1");
            workbook.write(output);
            bytes = output.toByteArray();
        }
        ParsedPvDataFile result = new ExcelPvDataFileParser().parse(
            new ByteArrayInputStream(bytes), 10);
        assertEquals(2, result.totalCount());
        assertEquals(1, result.rows().size());
        assertEquals("不允许公式单元格", result.errors().get(0).message());
    }
}
