package com.wf.monitor.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Bug 等待及通知记录，对应 bug_notify_record 表。
 * 字段使用驼峰命名；持久化时需映射到数据库的下划线列名。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BugNotifyRecord {

    /** 数据库自增主键，插入前为空。 */
    private Long id;

    /** Excel 物理行号，从 1 开始，对应 excel_row_number。 */
    private Integer excelRowNumber;

    /** 问题描述。 */
    private String problemDescription;

    /** Excel 中的研发负责人。 */
    private String ownerName;

    /** 匹配的研发人员 ID，对应 developer_mapping.id。 */
    private Long developerId;

    /** 0 待补全，1 待发送，2 已发送，3 结果待确认。 */
    private Integer notifyStatus = 0;

    /** 待补全原因，例如缺少描述或负责人未匹配。 */
    private String waitingReason;

    /** 实际发送使用的钉钉用户 ID 或手机号。 */
    private String recipientIdentity;

    /** 实际发送尝试次数。 */
    private Integer attemptCount = 0;

    /** 下次重试时间。 */
    private LocalDateTime nextRetryAt;

    /** 最近发送尝试时间。 */
    private LocalDateTime lastAttemptAt;

    /** 发送成功时间。 */
    private LocalDateTime sentAt;

    /** 最近发送错误，存储前需脱敏。 */
    private String lastError;

    /** 创建时间，由数据库生成。 */
    private LocalDateTime createdAt;

    /** 更新时间，由数据库维护。 */
    private LocalDateTime updatedAt;
}
