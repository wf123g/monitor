package com.wf.monitor;

import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Path;

@SpringBootTest
class MonitorApplicationTests {

    @Test
    void contextLoads() throws IOException {
        Path file = Path.of(
                "D:/computer1/AI超级智能体/monitor/"
                        + "src/main/resources/static/current.xlsx");
        //获取表
        try (Workbook workbook =
                     WorkbookFactory.create(file.toFile(), null, true)) {
            //获取表
            Sheet sheet = workbook.getSheet("全生命周期管理表");
            if (sheet == null) {
                throw new IllegalStateException("工作表不存在");
            }
            //获取header
            Row header = sheet.getRow(2);
            if (header == null) {
                throw new IllegalStateException("表头不存在");
            }
            DataFormatter formatter = new DataFormatter();
            int ownerColumn = -1;
            //找到研发负责人所在列
            for (Cell cell : header) {
                String name = formatter.formatCellValue(cell).trim();
                if (name.equals("问题描述")) {
                    ownerColumn = cell.getColumnIndex();
                }
            }
            System.out.println(ownerColumn);
        }

    }
}
