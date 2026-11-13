package com.wf.monitor;


import com.wf.monitor.mapper.bugNotifyRecord;
import com.wf.monitor.vo.BugMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class ExcelMonitorTask {
    @Autowired
    private bugNotifyRecord bugNotifyRecord;

    private static final Logger LOG =
            LoggerFactory.getLogger(ExcelMonitorTask.class);

    private final check checker;

    public ExcelMonitorTask(check checker) {
        this.checker = checker;
    }

    @Scheduled(fixedDelay = 300000)
    public void refreshAndCheck() {
        try {
            // 1. 下载最新 Excel
            newExcel.downloadExcel();

            // 2. 接收检查方法返回的列表

            checker.methodRows();
           //查找数据库中状态为1的消息
          List<BugMessage> message= bugNotifyRecord.selectBugByStatus(1);
            // 3. 交给下一步处理
            if (!message.isEmpty()) {
                handleRows(message);
            }
        } catch (Exception e) {
            LOG.error("定时下载、检查或后续处理失败", e);
        }
    }

    private void handleRows(List<BugMessage> message) {
        //这里进行钉钉发消息
        customRobotGroupMessage cu=new customRobotGroupMessage(message, bugNotifyRecord);
        cu.PostMessage();
    }
}
