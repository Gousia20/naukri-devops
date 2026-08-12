package com.adi.naukri.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

class TemplateBuilderTest {

    @Test
    void produces_valid_workbook_with_emails_sheet_and_five_samples()
            throws Exception {

        byte[] bytes = new TemplateBuilder().build();

        try (Workbook wb =
                     new XSSFWorkbook(new ByteArrayInputStream(bytes))) {

            Sheet s = wb.getSheet("Emails");

            assertNotNull(s);

            // Current Excel format:
            // name | email
            assertEquals(
                    "name",
                    s.getRow(0).getCell(0).getStringCellValue()
            );

            assertEquals(
                    "email",
                    s.getRow(0).getCell(1).getStringCellValue()
            );

            // Header + 5 sample rows
            assertEquals(5, s.getLastRowNum());
        }
    }
}