#!/bin/bash
# Java后端启动脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKEND_DIR="$PROJECT_DIR/knowledge-backend"

echo "=========================================="
echo "  Java后端服务启动"
echo "=========================================="

# 检查Java是否安装
if ! command -v java &> /dev/null; then
    echo "错误: Java未安装，请先安装JDK 17+"
    exit 1
fi

# 检查Java版本
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "错误: Java版本过低，需要JDK 17+，当前版本: $JAVA_VERSION"
    exit 1
fi

# 检查Maven是否安装
if ! command -v mvn &> /dev/null; then
    echo "错误: Maven未安装，请先安装Maven 3.8+"
    exit 1
fi

# 进入后端目录
cd "$BACKEND_DIR"

# 检查基础设施服务
echo "检查基础设施服务..."

# 检查MySQL
if ! curl -s http://localhost:3306 > /dev/null 2>&1; then
    echo "警告: MySQL可能未启动（端口3306）"
fi

# 检查Redis
if ! curl -s http://localhost:6379 > /dev/null 2>&1; then
    echo "警告: Redis可能未启动（端口6379）"
fi

# 检查Milvus
if ! curl -s http://localhost:19530 > /dev/null 2>&1; then
    echo "警告: Milvus可能未启动（端口19530）"
fi

# 检查MinerU
if curl -s http://localhost:8000/health > /dev/null 2>&1; then
    echo "✅ MinerU服务已启动"
else
    echo "警告: MinerU服务未启动（端口8000），将使用Tika作为解析器"
fi

# 启动后端服务
echo ""
echo "启动Java后端服务..."
echo "配置文件: application-dev.yml"
echo ""

mvn spring-boot:run -pl knowledge-admin -Dspring-boot.run.profiles=dev

echo "=========================================="
