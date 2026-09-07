#!/bin/bash
# 停止所有服务

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "  ERAG 停止所有服务"
echo "=========================================="

# 1. 停止MinerU服务
echo ""
echo "[1/2] 停止MinerU服务..."
cd "$PROJECT_DIR/mineru-service"
docker-compose down

# 2. 停止基础设施
echo ""
echo "[2/2] 停止基础设施服务..."
cd "$PROJECT_DIR/knowledge-backend"
docker-compose down

echo ""
echo "=========================================="
echo "  所有服务已停止"
echo "=========================================="
