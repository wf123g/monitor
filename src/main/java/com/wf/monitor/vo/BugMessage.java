package com.wf.monitor.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BugMessage {

    /** Excel 物理行号 */
    private Integer excelRowNumber;

    /** 问题描述 */
    private String problemDescription;

    /** 用于钉钉提醒的负责人身份(二选一即可) */
    private String dingTalkUserId;
    private String mobile;
}
