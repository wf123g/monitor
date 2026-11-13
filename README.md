# Excel Bug Monitor

基于 Java、Spring Boot、MyBatis、Apache POI 和 Playwright 的 Bug 通知工具：定时导出金山文档中的 Excel，识别追加行，将问题描述发送到钉钉群并 @ 对应研发负责人。

## 当前功能

- 复用浏览器登录态，导出并下载 Excel 快照。
- 按物理行号识别新增记录，保存扫描进度。
- 将负责人为空或未匹配的记录保存为待补全，下次扫描继续检查。
- 根据人员映射表取得钉钉用户 ID 或手机号，每条 Bug 单独发送群消息。
- 钉钉明确返回成功后，将记录标记为已发送，正常情况下后续扫描不会重复发送。
- 新记录入库与扫描进度更新在同一事务中执行。

流程：`导出 Excel → 扫描新增行/复查待补全记录 → 入库 → 查询待发送记录 → 钉钉通知 → 更新状态`。

当前每轮执行完成后等待 **5 分钟**再执行下一轮；启动后可能立即执行首次任务。尚未实现“点击收到”回调、管理页面、完整的失败重试管理或超时提醒。

## 运行环境

- JDK 17 或以上，Maven 3.9（也可使用仓库中的 Maven Wrapper）。
- MySQL 8，准备一个数据库及可读写该数据库的账号。
- 可显示浏览器窗口的桌面环境；当前使用 Playwright Chromium 有界面模式。
- 可访问目标金山文档、下载域名、钉钉服务及 Maven 依赖源的网络。
- 有目标文档查看和导出权限的金山文档账号，以及目标钉钉群的自定义机器人配置。

以下命令以 Windows PowerShell 为例。仓库不包含真实凭证、业务 Excel 或浏览器登录态。

## 1. 获取项目

```powershell
git clone https://github.com/wf123g/monitor.git
cd monitor
java -version
mvn -version
```

该仓库为私有仓库，克隆需要具有访问权限的 GitHub 身份。

## 2. 准备数据库

项目不会自动建表。新环境可在 MySQL 客户端执行以下初始化 SQL；已有表时先核对结构，不要重复初始化进度。

```sql
CREATE DATABASE IF NOT EXISTS monitor CHARACTER SET utf8mb4;
USE monitor;

CREATE TABLE developer_mapping (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_name VARCHAR(100) NOT NULL,
    dingtalk_user_id VARCHAR(128) DEFAULT NULL,
    mobile VARCHAR(32) DEFAULT NULL,
    enabled TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_name (owner_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bug_notify_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    excel_row_number INT UNSIGNED NOT NULL,
    problem_description TEXT,
    owner_name VARCHAR(100) DEFAULT NULL,
    developer_id BIGINT UNSIGNED DEFAULT NULL,
    notify_status TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '0待补全，1待发送，2已发送，3结果待确认（预留）',
    waiting_reason VARCHAR(255) DEFAULT NULL,
    recipient_identity VARCHAR(128) DEFAULT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME DEFAULT NULL,
    last_attempt_at DATETIME DEFAULT NULL,
    sent_at DATETIME DEFAULT NULL,
    last_error VARCHAR(1000) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_excel_row_number (excel_row_number),
    KEY idx_status_retry (notify_status, next_retry_at),
    KEY idx_developer (developer_id),
    CONSTRAINT fk_bug_notify_developer FOREIGN KEY (developer_id)
        REFERENCES developer_mapping (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE monitor_progress (
    id TINYINT UNSIGNED NOT NULL,
    read_row_number INT UNSIGNED NOT NULL DEFAULT 0,
    total_row_number INT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 新环境示例：跳过前三行表头，从 Excel 第4行开始读取。
-- 如果只通知今后新增的 Bug，将两个 3 改为当前表格的最后物理行号。
INSERT INTO monitor_progress (id, read_row_number, total_row_number)
VALUES (1, 3, 3);
```

在 `developer_mapping` 中添加实际人员：`owner_name` 必须与表内名称一致；`dingtalk_user_id`、`mobile` 至少配置一种有效身份，`enabled=1` 才参与发送。用户 ID 和手机号都按字符串保存，保留前导零。不要把真实人员数据提交到仓库。

进度使用 Excel 显示行号，从 1 开始。不要直接把 POI 的零起始下标写入进度。当前代码只更新 `read_row_number`，`total_row_number` 暂未自动维护；重试次数、发送时间和错误字段也尚未完整接入。

## 3. 调整表格路径与列位置

目前路径及表结构仍写在代码中，运行前必须按本机环境核对：

| 文件 | 需要核对的内容 |
| --- | --- |
| `src/main/java/com/wf/monitor/newExcel.java` | `OUTPUT`：导出快照保存路径 |
| `src/main/java/com/wf/monitor/check.java` | `Path file`：与 `OUTPUT` 指向同一个文件；工作表名称及列位置 |
| `src/test/java/com/wf/monitor/MonitorApplicationTests.java` | 测试读取的 Excel 路径及工作表名称 |

可以将下载和读取路径统一改为相对于项目根目录的 `src/main/resources/static/current.xlsx`。这不是现有环境变量配置项，需要修改对应 Java 路径。

当前约定：

- 工作表名称：`全生命周期管理表`。
- 表头在 Excel 第 3 行（POI 下标为 2）。
- 问题描述在 H 列（下标 7），研发负责人在 AB 列（下标 27）。
- 当前按固定列下标读取，不会根据表头自动重新定位。
- 单文件、单工作表，既有行不能排序、删除、复用或在中间插行；新增 Bug 应追加到末尾。

## 4. 配置环境变量

参考 [.env.example](.env.example)。**Java 和当前 Spring Boot 配置不会自动读取 `.env` 文件**，请在 IDEA 运行配置的环境变量中填写，或在启动应用的 PowerShell 会话中设置。

| 环境变量 | 是否必填 | 含义 |
| --- | --- | --- |
| `DINGTALK_ROBOT_TOKEN` | 是 | 自定义机器人 Webhook 中的 `access_token` 值，不是整个 Webhook URL |
| `DINGTALK_SIGNING_SECRET` | 是 | 机器人加签密钥 |
| `KDOCS_DOCUMENT_URL` | 是 | 要监控的金山文档完整 URL |
| `KDOCS_EXPORT_API` | 是 | 同一文档的完整导出接口基础 URL，必须以 `/` 结尾，不含最后的 `preload` 或 `result` |
| `MONITOR_DB_URL` | 否 | JDBC URL，默认 `jdbc:mysql://localhost:3306/monitor` |
| `MONITOR_DB_USERNAME` | 是 | MySQL 账号，无默认值 |
| `MONITOR_DB_PASSWORD` | 是 | MySQL 密码，无默认值 |

`KDOCS_EXPORT_API` 与文档分享地址不同。当前实现沿用金山文档网页的导出流程；可在有权限的浏览器会话中观察该文档导出请求取得对应基础地址。站点接口变更可能需要调整代码，不保证它是长期稳定的开放 API。

下面仅展示变量设置方式，必须替换占位内容后运行；不要将实际凭证保存回可提交文件。

```powershell
$env:DINGTALK_ROBOT_TOKEN = '<机器人Token>'
$env:DINGTALK_SIGNING_SECRET = '<机器人签名密钥>'
$env:KDOCS_DOCUMENT_URL = '<目标文档完整URL>'
$env:KDOCS_EXPORT_API = '<导出接口基础URL，以/结尾>'
$env:MONITOR_DB_URL = 'jdbc:mysql://localhost:3306/monitor'
$env:MONITOR_DB_USERNAME = '<数据库账号>'
$env:MONITOR_DB_PASSWORD = '<数据库密码>'
```

这些变量只作用于当前 PowerShell 进程及其启动的子进程。在另一个终端或 IDEA 中启动应用，需要在对应环境重新配置。修改变量后需重启应用。

## 5. 安装浏览器并启动

安装当前 Playwright 版本对应的 Chromium（首次运行需要下载）：

```powershell
mvn exec:java '-Dexec.mainClass=com.microsoft.playwright.CLI' '-Dexec.args=install chromium'
```

编译检查并启动：

```powershell
mvn -DskipTests compile
mvn spring-boot:run
```

未安装全局 Maven 时，可在项目根目录用 `./mvnw.cmd` 替代上述 `mvn`。

也可以在 IDEA 中运行 `com.wf.monitor.MonitorApplication`，先在该运行配置中填写环境变量。

启动前确认扫描起点及目标群：定时任务会自动运行，符合条件的记录会发送真实群消息。首次使用或登录过期时，在打开的 Chromium 窗口中完成金山文档登录。程序当前等待登录约 3 分钟，超时后本轮失败，下一轮重新尝试。

浏览器登录态保存于当前用户目录下的 `.kdocs-monitor/browser-profile`，不在项目仓库内。不要把该目录上传或分享。

## 运行验证与限制

建议先使用测试文档和测试群验证：追加一行、补填负责人、检查数据库状态是否从 `0`/`1` 变为 `2`，确认下轮不会再次发送成功记录。

- 成功返回只代表钉钉接受消息，不代表负责人已阅读或确认。
- 当前没有强制校验问题描述非空，也没有完整拦截人员映射中无有效接收身份的情况；录入时需保证信息完整。
- 已进入待发送状态的记录不会重新同步负责人变更；改派场景尚未完整处理。
- 已跳过的空行后续回填，可能不会再被识别。
- 明确发送失败的记录保留待发送状态，后续正常轮次再次尝试；没有完整的退避、重试上限或结果未知处理。
- 网络超时、发送成功后数据库更新失败等情况下，仍可能重复通知，不保证跨系统严格只发送一次。
- 导出失败会阻断本轮扫描及发送；实际通知延迟包含导出耗时和 5 分钟等待，不保证两分钟内完成。

现有 `@SpringBootTest` 会加载应用上下文及定时任务，并依赖本地 Excel 和数据库，不是隔离测试。不要在正式群配置下直接用 `mvn test` 做无副作用验证。本 README 的编译命令跳过测试。

## 仓库安全与相关文档

`.gitignore` 已排除业务 Excel 快照、`target/`、`.idea/`、`.env`、本地配置及日志。`.env.example` 仅保留空值示例。不要使用 `git add -f` 上传这些本地文件，也不要将真实凭证、导出地址或人员映射数据加入源码。

- [需求文档](需求文档.md)
- [技术设计文档](技术设计文档.md)

上述文档包含规划内容和待确认事项，实际已实现能力以当前代码及本 README 的限制说明为准。
