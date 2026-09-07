#!/bin/bash
# MinerU服务启动脚本（Conda环境）

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
MINERU_DIR="$PROJECT_DIR/mineru-service"

echo "=========================================="
echo "  MinerU PDF解析服务启动（Conda模式）"
echo "=========================================="

# 检查Conda是否安装
if ! command -v conda &> /dev/null; then
    echo "错误: Conda未安装，请先安装Conda"
    echo "安装指南: https://docs.conda.io/en/latest/miniconda.html"
    exit 1
fi

# 进入MinerU目录
cd "$MINERU_DIR"

# 检查rag环境是否存在
if ! conda env list | grep -q "^rag "; then
    echo "创建conda环境: rag"
    conda env create -f environment.yml
else
    echo "conda环境rag已存在"
fi

# 激活环境并启动服务
echo "激活conda环境并启动服务..."
echo "注意: 请手动执行以下命令:"
echo ""
echo "  conda activate rag"
echo "  cd $MINERU_DIR"
echo "  python run.py"
echo ""
echo "=========================================="
