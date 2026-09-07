/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.tool;

import com.knowledge.agent.config.AgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link SqlTool} 单元测试：聚焦安全校验路径（只读强制 / 表白名单 / 注入防护）。
 * <p>执行结果映射依赖真实 JDBC 链路，由集成测试覆盖；本类以 DataSource 抛异常验证「校验通过后进入执行」。
 */
@ExtendWith(MockitoExtension.class)
class SqlToolTest {

    @Mock
    private DataSource dataSource;

    private AgentProperties props;

    @BeforeEach
    void setUp() {
        props = new AgentProperties();
        props.getTool().getSql().setAllowedTables(List.of("kb_document", "agent_task"));
    }

    @Test
    void should_reject_delete_statement() {
        assertRejected("DELETE FROM kb_document WHERE id=1", "只读语句");
    }

    @Test
    void should_reject_update_statement() {
        assertRejected("UPDATE kb_document SET status=2 WHERE id=1", "只读语句");
    }

    @Test
    void should_reject_insert_statement() {
        assertRejected("INSERT INTO kb_document (id) VALUES (1)", "只读语句");
    }

    @Test
    void should_reject_drop_statement() {
        assertRejected("DROP TABLE kb_document", "只读语句");
    }

    @Test
    void should_reject_semicolon_injection() {
        assertRejected("SELECT * FROM kb_document; DROP TABLE kb_document", "禁用字符");
    }

    @Test
    void should_reject_line_comment_injection() {
        assertRejected("SELECT * FROM kb_document -- comment", "禁用字符");
    }

    @Test
    void should_reject_block_comment_injection() {
        assertRejected("SELECT * FROM /*x*/ kb_document", "禁用字符");
    }

    @Test
    void should_reject_when_whitelist_empty() {
        props.getTool().getSql().setAllowedTables(List.of());
        SqlTool tool = new SqlTool(dataSource, props);
        ToolResult result = tool.execute(newContext(),
                Map.of("sql", "SELECT * FROM kb_document"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未配置"));
    }

    @Test
    void should_reject_table_not_in_whitelist() {
        SqlTool tool = new SqlTool(dataSource, props);
        ToolResult result = tool.execute(newContext(),
                Map.of("sql", "SELECT * FROM kb_user"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("不在白名单"));
        assertTrue(result.getErrorMessage().contains("kb_user"));
    }

    @Test
    void should_extract_table_from_schema_qualified_name() {
        // kb_base.kb_document → 提取 kb_document，命中白名单（校验通过，进入执行）
        assertValidationPassed("SELECT * FROM kb_base.kb_document");
    }

    @Test
    void should_pass_validation_for_valid_select() {
        assertValidationPassed("SELECT id, status FROM kb_document WHERE status = 2");
    }

    @Test
    void should_pass_validation_for_show_statement() {
        // SHOW 无 FROM/JOIN，不做表白名单校验；前缀白名单通过
        assertValidationPassed("SHOW TABLES");
    }

    @Test
    void should_pass_validation_for_describe_statement() {
        assertValidationPassed("DESC kb_document");
    }

    @Test
    void should_not_require_auth() {
        SqlTool tool = new SqlTool(dataSource, props);
        assertFalse(tool.authRequired(), "SQL 工具为副作用工具，由角色级授权控制");
    }

    @Test
    void should_have_expected_tool_name() {
        SqlTool tool = new SqlTool(dataSource, props);
        assertEquals("sql_query", tool.name());
    }

    @Test
    void should_reject_empty_sql() {
        SqlTool tool = new SqlTool(dataSource, props);
        ToolResult result = tool.execute(newContext(), Map.of("sql", "  "));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("不能为空"));
    }

    // ==================== 测试辅助 ====================

    /** 断言 SQL 被校验拒绝，且错误信息包含期望片段（未进入执行，不触达 DataSource） */
    private void assertRejected(String sql, String expectedFragment) {
        SqlTool tool = new SqlTool(dataSource, props);
        ToolResult result = tool.execute(newContext(), Map.of("sql", sql));
        assertFalse(result.isSuccess(), "SQL 应被拒绝: " + sql);
        assertTrue(result.getErrorMessage().contains(expectedFragment),
                "错误信息应包含[" + expectedFragment + "]，实际: " + result.getErrorMessage());
    }

    /**
     * 断言校验通过并进入执行：DataSource.getConnection() 抛异常使执行失败，
     * 若错误信息为「SQL 查询失败」则证明校验门已放行（而非校验拒绝）。
     */
    private void assertValidationPassed(String sql) {
        try {
            when(dataSource.getConnection()).thenThrow(new SQLException("mock-ds"));
        } catch (SQLException e) {
            // thenThrow 声明 SQLException，构造不会抛
        }
        SqlTool tool = new SqlTool(dataSource, props);
        ToolResult result = tool.execute(newContext(), Map.of("sql", sql));
        assertFalse(result.isSuccess(), "执行应因 mock DataSource 失败");
        assertTrue(result.getErrorMessage().contains("SQL 查询失败"),
                "校验应通过并进入执行，实际: " + result.getErrorMessage());
    }

    private static ToolContext newContext() {
        return ToolContext.builder()
                .tenantId(1L).userId(10L).kbId(7L).taskId(100L).stepId(null).build();
    }
}
