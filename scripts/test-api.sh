#!/bin/bash
# API测试脚本

set -e

BASE_URL="http://localhost:8080/api"
MINERU_URL="http://localhost:8000"

echo "=========================================="
echo "  ERAG API测试"
echo "=========================================="

# 1. 测试MinerU健康检查
echo ""
echo "[1/5] 测试MinerU健康检查..."
if curl -s "$MINERU_URL/health" | grep -q "ok"; then
    echo "✅ MinerU服务正常"
else
    echo "❌ MinerU服务异常"
fi

# 2. 测试后端健康检查
echo ""
echo "[2/5] 测试后端健康检查..."
if curl -s "$BASE_URL/actuator/health" | grep -q "UP"; then
    echo "✅ 后端服务正常"
else
    echo "❌ 后端服务异常"
fi

# 3. 测试文档上传接口
echo ""
echo "[3/5] 测试文档上传接口..."
echo "请准备一个PDF文件，然后执行:"
echo "  curl -X POST $BASE_URL/kb/document/upload \\"
echo "    -F 'file=@/path/to/test.pdf' \\"
echo "    -F 'kbId=1'"

# 4. 测试问答接口
echo ""
echo "[4/5] 测试问答接口..."
echo "请执行:"
echo "  curl -X POST $BASE_URL/ai/ask \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"question\": \"你好\", \"kbId\": 1}'"

# 5. 测试MinerU解析接口
echo ""
echo "[5/5] 测试MinerU解析接口..."
echo "请执行:"
echo "  curl -X POST $MINERU_URL/parse \\"
echo "    -F 'file=@/path/to/test.pdf'"

echo ""
echo "=========================================="
echo "  测试完成"
echo "=========================================="
