package com.wf.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.RequestOptions;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 复用登录态，创建导出任务并自动保存 Excel 快照。 */
public class newExcel {
    private static final Logger LOG = LoggerFactory.getLogger(newExcel.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ORIGIN = "https://www.kdocs.cn";
    private static final String DOCUMENT_URL = System.getenv("KDOCS_DOCUMENT_URL");
    private static final String EXPORT_API = System.getenv("KDOCS_EXPORT_API");
    // 来自实际 preload 请求，解码后为当前文档站点的 host。
    private static final String ATTRS = "eyJob3N0IjoiaHR0cHM6Ly93d3cua2RvY3MuY24ifQ%3D%3D";
    private static final Path OUTPUT = Path.of(
            "D:/computer1/AI超级智能体/monitor/src/main/resources/static/current.xlsx");


    /** 自动下载并更新 current.xlsx；完成后返回，失败时抛出异常。 */
    public static void downloadExcel() throws Exception {
        if (DOCUMENT_URL == null || DOCUMENT_URL.isBlank()
                || EXPORT_API == null || EXPORT_API.isBlank()) {
            throw new IllegalStateException("请设置 KDOCS_DOCUMENT_URL 和 KDOCS_EXPORT_API 环境变量");
        }
        if (!EXPORT_API.endsWith("/")) {
            throw new IllegalStateException("KDOCS_EXPORT_API 必须以 / 结尾");
        }
        Path profile = Path.of(System.getProperty("user.home"), ".kdocs-monitor", "browser-profile");
        Files.createDirectories(profile);
        Files.createDirectories(OUTPUT.getParent());
        try (Playwright playwright = Playwright.create();
             BrowserContext context = playwright.chromium().launchPersistentContext(
                     profile, new BrowserType.LaunchPersistentContextOptions().setHeadless(false))) {
            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
            page.navigate(DOCUMENT_URL);
            LOG.info("等待文档登录态；首次使用或登录过期时，请在打开的浏览器中登录。"
                    + "登录完成后会自动导出，无需按回车或点击下载。");
            page.waitForCondition(() -> !csrfToken(context).isBlank()
                            && page.getByRole(AriaRole.BUTTON,
                            new Page.GetByRoleOptions().setName("分享").setExact(true)).isVisible(),
                    new Page.WaitForConditionOptions().setTimeout(180_000));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("format", "xlsx");
            payload.put("attrs", ATTRS);
            payload.put("store_type", "ks3");
            payload.put("active_sheet_id", 1);
            // 每次导出生成客户端请求标识，不复用抓包中的会话标识。
            payload.put("clientId", UUID.randomUUID().toString().replace("-", ""));
            LOG.info("自动创建 Excel 导出任务。");
            JsonNode task = post(context, "preload", payload);
            String taskId = requiredText(task, "task_id");
            String taskType = requiredText(task, "task_type");
            LOG.info("导出任务已创建，开始等待处理结果。");

            Map<String, Object> pollPayload = new LinkedHashMap<>();
            pollPayload.put("task_id", taskId);
            pollPayload.put("task_type", taskType);
            pollPayload.put("with_size", true);
            URI downloadUri = awaitResult(context, page, pollPayload);
            downloadAndSave(downloadUri);
        }
    }

    private static String csrfToken(BrowserContext context) {
        return context.cookies(ORIGIN).stream()
                .filter(cookie -> "csrf".equals(cookie.name) || "csrftoken".equals(cookie.name)
                        || "csrfmiddlewaretoken".equals(cookie.name))
                .map(cookie -> cookie.value)
                .findFirst().orElse("");
    }

    private static JsonNode post(BrowserContext context, String endpoint,
                                 Map<String, Object> payload) throws Exception {
        String csrf = csrfToken(context);
        if (csrf.isBlank()) {
            throw new IllegalStateException("当前浏览器会话没有 CSRF 参数，请重新登录后运行。");
        }
        Map<String, Object> body = new LinkedHashMap<>(payload);
        body.put("csrfmiddlewaretoken", csrf);
        APIResponse response = context.request().post(EXPORT_API + endpoint,
                RequestOptions.create().setData(body)
                        .setHeader("Origin", ORIGIN)
                        .setHeader("Referer", DOCUMENT_URL)
                        .setHeader("Accept", "application/json")
                        .setHeader("X-CSRFToken", csrf)
                        .setTimeout(30_000).setMaxRedirects(0));
        try {
            if (!response.ok()) {
                throw new IllegalStateException(endpoint + " 请求失败，HTTP " + response.status()
                        + "；401/403 请检查登录态和导出权限。");
            }
            JsonNode result;
            try {
                result = JSON.readTree(response.body());
            } catch (Exception e) {
                throw new IllegalStateException(endpoint + " 未返回有效 JSON，请检查登录是否失效。");
            }
            if (result == null || !result.isObject()) {
                throw new IllegalStateException(endpoint + " 返回格式异常。");
            }
            // 不记录响应正文，避免任务标识、登录参数和签名链接进入日志。
            if (result.has("code") && !"0".equals(result.path("code").asText())) {
                throw new IllegalStateException(endpoint + " 返回业务错误，code="
                        + result.path("code").asText());
            }
            return result;
        } finally {
            response.dispose();
        }
    }

    private static URI awaitResult(BrowserContext context, Page page,
                                   Map<String, Object> payload) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
        int checks = 0;
        while (System.nanoTime() < deadline) {
            JsonNode result = post(context, "result", payload);
            String status = requiredText(result, "status");
            if ("finished".equals(status)) {
                String url = requiredText(result.path("data"), "url");
                URI uri = URI.create(url);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || !(uri.getHost().equals("wpscdn.cn") || uri.getHost().endsWith(".wpscdn.cn"))
                        || uri.getUserInfo() != null) {
                    throw new IllegalStateException("导出返回了非预期的下载域名，未发起下载。");
                }
                return uri;
            }
            if (!"running".equals(status)) {
                throw new IllegalStateException("导出未完成，接口返回状态：" + status);
            }
            if (checks++ % 5 == 0) {
                LOG.info("服务端正在生成 Excel，已查询 {} 次。", checks);
            }
            page.waitForTimeout(2_000);
        }
        throw new IllegalStateException("等待导出超过 5 分钟，原 current.xlsx 保持不变。");
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalStateException("接口响应缺少字段：" + field);
        }
        return value;
    }

    private static void downloadAndSave(URI uri) throws Exception {
        Path temporary = Files.createTempFile(OUTPUT.getParent(), "snapshot-", ".tmp");
        try {
            LOG.info("导出完成，正在自动下载 Excel。");
            // 签名 URL 自带下载授权；不向 CDN 转发文档 Cookie 或 CSRF 参数。
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(5)).GET().build();
            HttpResponse<Path> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            } catch (java.io.IOException e) {
                throw new IllegalStateException("下载连接失败，原 current.xlsx 保持不变。");
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException("下载失败，HTTP " + response.statusCode()
                        + "；请重新运行以创建新的导出任务。");
            }
            // 使用事件读取器验证工作簿，避免将约 50 MB 文件的全部单元格加载到内存。
            try (OPCPackage pkg = OPCPackage.open(temporary.toFile(), PackageAccess.READ)) {
                XSSFReader reader = new XSSFReader(pkg);
                Iterator<InputStream> sheets = reader.getSheetsData();
                int count = 0;
                while (sheets.hasNext()) {
                    try (InputStream sheet = sheets.next()) {
                        if (sheet.read() == -1) {
                            throw new IllegalStateException("导出的工作表内容为空。");
                        }
                    }
                    count++;
                }
                if (count == 0) {
                    throw new IllegalStateException("导出的 Excel 没有工作表。");
                }
                LOG.info("Excel 文件结构校验通过，共 {} 个工作表。", count);
            }
            try {
                Files.move(temporary, OUTPUT,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, OUTPUT, StandardCopyOption.REPLACE_EXISTING);
            }
            LOG.info("下载完成：{}（{} 字节）", OUTPUT, Files.size(OUTPUT));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
