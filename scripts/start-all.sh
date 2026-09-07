#!/bin/bash
# 一键启动所有服务

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "  ERAG 一键启动"
echo "=========================================="

# 1. 启动基础设施
echo ""
echo "[1/3] 启动基础设施服务..."
cd "$PROJECT_DIR/knowledge-backend"
docker-compose up -d

echo "等待基础设施启动..."
sleep 10

# 检查基础设施
echo "检查基础设施状态..."
for port in 3306 6379 19530 9200 5672; do
    if nc -z localhost $port 2>/dev/null; then
        echo "  ✅ 端口 $port 正常"
    else
        echo "  ⚠️ 端口 $port 可能未就绪"
    fi
done

# 2. 启动MinerU服务
echo ""
echo "[2/3] 启动MinerU服务..."
cd "$PROJECT_DIR/mineru-service"
docker-compose up -d

echo "等待MinerU启动..."
sleep 5

if curl -s http://localhost:8000/health > /dev/null; then
    echo "✅ MinerU服务启动成功"
else
    echo "⚠️ MinerU服务可能未就绪，请稍后检查"
fi

# 3. 启动Java后端
echo ""
echo "[3/3] 启动Java后端服务..."
echo "请在新的终端窗口执行:"
echo ""
echo "  cd $PROJECT_DIR/knowledge-backend"
echo "  mvn spring-boot:run -pl knowledge-admin -Dspring-boot.run.profiles=dev"
echo ""
echo "=========================================="
echo "  启动完成！"
echo "=========================================="
echo ""
echo "服务地址:"
echo "  - 后端API: http://localhost:8080/api"
echo "  - MinerU: http://localhost:8000"
echo "  - Swagger: http://localhost:8080/api/swagger-ui.html"
echo "  - RabbitMQ管理: http://localhost:15672"
echo "  - MinIO控制台: http://localhost:9001"
echo ""
