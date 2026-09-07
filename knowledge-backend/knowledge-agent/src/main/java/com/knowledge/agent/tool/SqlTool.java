package com.knowledge.agent.tool;

import com.knowledge.agent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只读 SQL 查询工具：受表白名单 + 行数限制 + 语句类型强制约束。
 * <p>
 * <b>只读强制</b>：仅允许 SELECT / WITH / SHOW / DESC / DESCRIBE / EXPLAIN 开头；
 * 拒绝分号（防多语句）与注释符（{@code --} / {@code /*} / {@code #}，防注释绕过）。
 * <p>
 * <b>表白名单</b>：从 FROM/JOIN 子句提取表名，必须全部命中 {@code agent.tool.sql.allowed-tables}；
 * 未配置白名单时拒绝所有查询（默认拒绝）。
 * <p>
 * <b>资源限制</b>：返回行数 ≤ {@code agent.tool.sql.max-rows}，单查询超时 ≤ {@code query-timeout-seconds}。
 * <p>
 * <b>副作用工具授权</b>：authRequired=false，由 tool_permission 角色级授权控制。
 * <p>
 * <b>实现</b>：每次查询新建 {@link JdbcTemplate}（轻量）以应用 per-tool 超时与行数上限，
 * 不污染共享 JdbcTemplate 实例。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlTool implements Tool {

    private final DataSource dataSource;
    private final AgentProperties props;

    /** 只读语句前缀白名单（大小写不敏感） */
    private static final List<String> READONLY_PREFIXES = List.of(
            "SELECT", "WITH", "SHOW", "DESC", "DESCRIBE", "EXPLAIN");

    /** 提取 FROM/JOIN 后的表名（支持 schema.table，取最后一段做白名单匹配） */
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "\\b(?:FROM|JOIN)\\s+([a-zA-Z_][\\w.]*)", Pattern.CASE_INSENSITIVE);

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "sql": {"type": "string", "description": "只读SQL(SELECT/SHOW/DESC/EXPLAIN)"},
                "params": {"type": "array", "description": "预编译参数"},
                "limit": {"type": "integer", "description": "返回行数上限", "default": 100}
              },
              "required": ["sql"]
            }""";

    @Override
    public String name() {
        return "sql_query";
    }

    @Override
    public String description() {
        return "只读 SQL 查询(SELECT/SHOW/DESC/EXPLAIN)，受表白名单+行数限制。"
                + "仅允许查询白名单内表，返回行数受限。";
    }

    @Override
    public String parametersJsonSchema() {
        return SCHEMA;
    }

    @Override
    public boolean authRequired() {
        return false;
    }

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> arguments) {
        String sql = String.valueOf(arguments.get("sql")).trim();
        if (sql.isEmpty()) {
            return ToolResult.failure("SQL(sql)不能为空");
        }

        // 1. 语句类型 + 注入防护校验
        String typeError = validateReadonly(sql);
        if (typeError != null) {
            return ToolResult.failure(typeError);
        }

        // 2. 表白名单校验
        String tableError = validateTables(sql);
        if (tableError != null) {
            return ToolResult.failure(tableError);
        }

        // 3. 解析参数与行数上限
        int maxRows = props.getTool().getSql().getMaxRows();
        if (arguments.containsKey("limit") && arguments.get("limit") != null) {
            int requested = ((Number) arguments.get("limit")).intValue();
            if (requested > 0 && requested < maxRows) {
                maxRows = requested;
            }
        }
        Object[] params = toParamsArray(arguments.get("params"));

        // 4. 执行查询（per-tool 超时 + 行数上限）
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(props.getTool().getSql().getQueryTimeoutSeconds());
        jdbcTemplate.setMaxRows(maxRows);

        final int finalMaxRows = maxRows;
        ResultSetExtractor<Map<String, Object>> extractor = rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next() && rows.size() < finalMaxRows) {
                Map<String, Object> row = new LinkedHashMap<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    row.put(columns.get(i - 1), rs.getObject(i));
                }
                rows.add(row);
            }
            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("columns", columns);
            resultData.put("rows", rows);
            resultData.put("rowCount", rows.size());
            resultData.put("truncated", rows.size() >= finalMaxRows);
            return resultData;
        };

        Map<String, Object> data;
        try {
            data = jdbcTemplate.query(sql, extractor, params);
        } catch (Exception e) {
            log.warn("[SqlQuery] 查询失败 task={} sql={}: {}", ctx.getTaskId(), sql, e.getMessage());
            return ToolResult.failure("SQL 查询失败: " + e.getMessage());
        }

        log.info("[SqlQuery] task={} rows={} sql={}", ctx.getTaskId(),
                data.get("rowCount"), abbreviate(sql));
        return ToolResult.success(data);
    }

    /** 只读语句校验：前缀白名单 + 拒绝分号/注释符 */
    private String validateReadonly(String sql) {
        if (sql.contains(";") || sql.contains("--") || sql.contains("/*") || sql.contains("#")) {
            return "SQL 包含禁用字符(分号/注释符)，仅允许单条只读语句";
        }
        String upper = sql.toUpperCase();
        boolean ok = false;
        for (String prefix : READONLY_PREFIXES) {
            if (upper.startsWith(prefix + " ") || upper.startsWith(prefix + "\t")
                    || upper.startsWith(prefix + "\n") || upper.equals(prefix)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            return "仅允许只读语句(SELECT/WITH/SHOW/DESC/EXPLAIN)";
        }
        return null;
    }

    /** 表白名单校验：提取 FROM/JOIN 表名，逐个校验 */
    private String validateTables(String sql) {
        List<String> allowed = props.getTool().getSql().getAllowedTables();
        if (allowed == null || allowed.isEmpty()) {
            return "未配置 SQL 表白名单(agent.tool.sql.allowed-tables)，拒绝所有查询";
        }
        List<String> allowedLower = allowed.stream().map(String::toLowerCase).toList();

        Matcher matcher = TABLE_PATTERN.matcher(sql);
        List<String> referenced = new ArrayList<>();
        while (matcher.find()) {
            String raw = matcher.group(1);
            // schema.table → 取最后一段做匹配
            String table = raw.contains(".") ? raw.substring(raw.lastIndexOf('.') + 1) : raw;
            referenced.add(table);
        }
        for (String table : referenced) {
            if (!allowedLower.contains(table.toLowerCase())) {
                return "表 [" + table + "] 不在白名单内（白名单: " + allowed + "）";
            }
        }
        return null;
    }

    /** 入参 params（List）转 Object[] */
    @SuppressWarnings("unchecked")
    private static Object[] toParamsArray(Object params) {
        if (params instanceof List<?> list) {
            return list.toArray();
        }
        return new Object[0];
    }

    private static String abbreviate(String sql) {
        return sql.length() > 120 ? sql.substring(0, 120) + "..." : sql;
    }
}
