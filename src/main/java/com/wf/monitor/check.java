package com.wf.monitor;

import com.wf.monitor.entity.BugNotifyRecord;
import com.wf.monitor.mapper.bugNotifyRecord;
import com.wf.monitor.mapper.developMapper;
import com.wf.monitor.mapper.monitorProgress;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.*;
@Service
public class check {
    @Autowired
    private  monitorProgress progressMapper;
    @Autowired
    private bugNotifyRecord  bugNotifyRecordMapper;
@Autowired
private developMapper developerMapper;

    private static final int OwnerColumn = 27;
    private static final int PROBLEM_DESCRIPTION_Column=7;
    private static final String LACK_OWNER="缺少研发负责人";

@Transactional
    // 去查表进行校验
    public void methodRows(){

        //创建文件路径
        Path file = Path.of(
                "D:/computer1/AI超级智能体/monitor/"
                        + "src/main/resources/static/current.xlsx");
        //获取表
        try (Workbook workbook =
                     WorkbookFactory.create(file.toFile(), null, true)){
            //获取表
            Sheet sheet = workbook.getSheet("全生命周期管理表");
            if (sheet == null) {
                throw new IllegalStateException("工作表不存在");
            }
            //获取header
            Row header = sheet.getRow(2);
            if(header==null){
                throw new IllegalStateException("表头不存在");
            }
            DataFormatter formatter = new DataFormatter();
            //记录到的已读取行数
            Integer readRowNumber = progressMapper.selectLastRows();
            if (readRowNumber == null) {
                throw new IllegalStateException("请先初始化 monitor_progress 表中 id=1 的记录");
            }
            // 数据库保存从 1 开始的 Excel 行号，POI 使用从 0 开始的下标。
            int lastRows = readRowNumber - 1;
            //开始比较
            //获取最后一行下标
            int lastRow=sheet.getLastRowNum();//从0开始的下标
            int count=0;
            if(lastRow>lastRows){
                for(int i=lastRow;i>lastRows;i--){
                    Row row = sheet.getRow(i);
                    if(row==null){
                        //该行为空白行时跳过即可不抛异常
                        count++;
                       continue;
                    }
                    //获取该行对应列数据
                    Cell cell = row.getCell(OwnerColumn);
                    String name = formatter.formatCellValue(cell).trim();
                    Cell c=row.getCell(PROBLEM_DESCRIPTION_Column);
                    String problemdescription=formatter.formatCellValue(c).trim();
                    BugNotifyRecord bnr = new BugNotifyRecord();
                    //若没有负责人则记录等待下一次查询比对
                    if(name.isBlank()){
                        //物理行号
                        bnr.setExcelRowNumber(i+1);
                        //问题描述
                        bnr.setProblemDescription(problemdescription);
                        //状态（0）
                        bnr.setNotifyStatus(0);
                        //等待原因
                        bnr.setWaitingReason(LACK_OWNER);
                        bnr.setOwnerName(null);
                        bugNotifyRecordMapper.insertBug(bnr);

                    }
                    else {
                        Long id = developerMapper.selectIdByName(name);

                            bnr.setExcelRowNumber(i + 1);              // Excel 物理行号
                            bnr.setProblemDescription(problemdescription);
                            bnr.setOwnerName(name);                    // 研发负责人
                            bnr.setDeveloperId(id);
                        if (id != null){
                            bnr.setNotifyStatus(1);
                        }
                        else {
                            bnr.setNotifyStatus(0);
                        }
                            bnr.setWaitingReason(null);                // 无需等待
                            bugNotifyRecordMapper.insertBug(bnr);


                    }
                    count++;

                }
                progressMapper.updateReadRows(count);
            }
            //接着查pendingOwnerRows集合 查物理行号
            List<Integer>pendingOwnerRows=bugNotifyRecordMapper.selectRowsByStatus(0);
           for(int i=0;i<pendingOwnerRows.size();i++) {
                int row=pendingOwnerRows.get(i);
                Row r = sheet.getRow(row-1);
                if (r == null) {
                    continue;
                }
                Cell cell = r.getCell(OwnerColumn);
                String name = formatter.formatCellValue(cell).trim();
                if (name.isEmpty()) {
                    continue; // 负责人仍为空，保留到下次检查
                }
               Cell cell1 = r.getCell(PROBLEM_DESCRIPTION_Column);
                String problemdescription=formatter.formatCellValue(cell1).trim();
               //不为空根据name去更新状态和删除等待原因
               BugNotifyRecord bnr = new BugNotifyRecord();
                Long id= developerMapper.selectIdByName(name);
                if (id == null) {
                    continue; // 未匹配负责人时保留待补全状态，下一轮继续检查。
                }
                bnr.setExcelRowNumber(row );
                bnr.setOwnerName(name);
                bnr.setDeveloperId(id);
                bnr.setNotifyStatus(1);
                bnr.setWaitingReason(null);
                bnr.setProblemDescription(problemdescription);
                bugNotifyRecordMapper.updateByBnr(bnr);

            }
        }catch(Exception ignored){
            throw new IllegalStateException("检查 Excel 失败", ignored);
        }


    }
}
