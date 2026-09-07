# MinerU PDF解析服务

基于MinerU的PDF文档解析服务，将PDF转换为结构化Markdown格式。

## 功能特性

- PDF转Markdown（保留标题层级、表格、列表等结构）
- 自动提取文档标题
- 表格识别与Markdown格式输出
- 支持批量解析
- RESTful API接口

## 环境要求

- Python 3.10+
- Conda（推荐）
- Docker（可选）

## 快速开始

### 方式一：Conda环境（本地开发）

```bash
# 1. 创建conda环境
conda env create -f environment.yml

# 2. 激活环境
conda activate rag

# 3. 启动服务
python run.py
```

### 方式二：Docker部署

```bash
# 1. 构建镜像
docker build -t mineru-service .

# 2. 启动容器
docker-compose up -d

# 3. 查看日志
docker-compose logs -f
```

## API文档

### 健康检查

```
GET /health
```

响应：
```json
{
  "status": "ok",
  "service": "mineru",
  "version": "0.7.0"
}
```

### 解析PDF

```
POST /parse
Content-Type: multipart/form-data
```

参数：
- `file`: PDF文件（必需）
- `enable_ocr`: 是否启用OCR（可选，默认false）

响应：
```json
{
  "status": "success",
  "markdown": "# 文档标题\n\n正文内容...",
  "title": "文档标题",
  "total_pages": 10,
  "tables": [
    {
      "page_number": 1,
      "content": "| 列1 | 列2 |\n|---|---|\n| 数据1 | 数据2 |",
      "rows": 1,
      "cols": 2
    }
  ],
  "message": "解析成功，耗时2.35秒"
}
```

## 测试接口

```bash
# 健康检查
curl http://localhost:8000/health

# 解析PDF
curl -X POST http://localhost:8000/parse \
  -F "file=@/path/to/document.pdf"
```

## 配置说明

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| MINERU_HOST | 0.0.0.0 | 监听地址 |
| MINERU_PORT | 8000 | 监听端口 |
| MINERU_LOG_LEVEL | INFO | 日志级别 |
| MINERU_TEMP_DIR | /tmp/mineru | 临时文件目录 |
| MINERU_MAX_FILE_SIZE_MB | 100 | 最大文件大小(MB) |

## 常见问题

### 1. MinerU安装失败

```bash
# 尝试使用国内镜像
pip install magic-pdf[full] -i https://pypi.tuna.tsinghua.edu.cn/simple
```

### 2. 解析速度慢

- 大PDF文件解析需要较长时间，建议调整超时配置
- 可以启用GPU加速（需要CUDA支持）

### 3. 表格识别不准确

MinerU对复杂表格的识别可能不完美，可以：
- 尝试启用OCR模式
- 对于特别复杂的表格，考虑使用Vision模型辅助识别

## 相关链接

- [MinerU GitHub](https://github.com/opendatalab/MinerU)
- [FastAPI文档](https://fastapi.tiangolo.com/)
