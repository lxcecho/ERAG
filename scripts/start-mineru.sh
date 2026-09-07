#!/bin/bash
# MinerU服务启动脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
MINERU_DIR="$PROJECT_DIR/mineru-service"

echo "=========================================="
echo "  MinerU PDF解析服务启动"
echo "=========================================="

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo "错误: Docker未安装，请先安装Docker"
    exit 1
fi

# 检查Docker Compose是否安装
if ! command -v docker-compose &> /dev/null; then
    echo "错误: Docker Compose未安装，请先安装Docker Compose"
    exit 1
fi

# 进入MinerU目录
cd "$MINERU_DIR"

# 启动服务
echo "启动MinerU服务..."
docker-compose up -d

# 等待服务启动
echo "等待服务启动..."
sleep 5

# 检查服务状态
echo "检查服务状态..."
if curl -s http://localhost:8000/health > /dev/null; then
    echo "✅ MinerU服务启动成功"
    echo "   服务地址: http://localhost:8000"
    echo "   API文档: http://localhost:8000/docs"
else
    echo "❌ MinerU服务启动失败"
    echo "请查看日志: docker-compose logs mineru"
    exit 1
fi

echo "=========================================="
