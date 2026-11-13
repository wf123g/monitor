package com.wf.monitor.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Excel 扫描进度，对应 monitor_progress 表。
 * 行号使用 Excel 显示行号，从 1 开始；0 表示尚未扫描或获取。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonitorProgress {

    /** 固定为 1，单工作表监控。 */
    private Integer id = 1;

    /** 已扫描并成功入库的最后一行，对应 read_row_number。 */
    private Integer readRowNumber = 0;

    /** 最近一次成功读取的 Excel 最后一行，对应 total_row_number。 */
    private Integer totalRowNumber = 0;

    /** 更新时间，由数据库维护，对应 updated_at。 */
    private LocalDateTime updatedAt;
}
