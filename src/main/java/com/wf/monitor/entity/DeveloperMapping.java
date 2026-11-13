package com.wf.monitor.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 研发负责人与钉钉身份映射，对应 developer_mapping 表。
 * 字段使用驼峰命名；持久化时需映射到数据库的下划线列名。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeveloperMapping {

    /** 数据库自增主键，插入前为空。 */
    private Long id;

    /** Excel 中的负责人名称。 */
    private String ownerName;

    /** 钉钉用户 ID。 */
    private String dingtalkUserId;

    /** 手机号，按提醒方式配置。 */
    private String mobile;

    /** 0 禁用，1 启用。 */
    private Integer enabled = 1;

    /** 创建时间，由数据库生成。 */
    private LocalDateTime createdAt;

    /** 更新时间，由数据库维护。 */
    private LocalDateTime updatedAt;
}
