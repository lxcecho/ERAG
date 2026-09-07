-- ERAG Database Baseline Migration
-- 此文件为 Flyway 基线，对应 deploy/init.sql 的初始 schema
-- 后续增量变更使用 V2__, V3__... 序号管理

-- 说明：
-- 1. 首次部署请先执行 deploy/init.sql 初始化完整 schema
-- 2. 然后执行 Flyway baseline 将现有 schema 标记为已迁移
-- 3. 后续变更通过新的 V{N}__xxx.sql 文件管理

-- 基线标记（Flyway 会自动跳过此文件，仅用于版本追踪）
-- 实际表结构参见 deploy/init.sql
SELECT 1 AS baseline_marker;
