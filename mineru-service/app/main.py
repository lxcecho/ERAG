"""
MinerU PDF解析服务 - FastAPI主入口
"""
import logging
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, UploadFile, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .config import service_config, mineru_config
from .models import ParseResponse, HealthResponse, ResponseStatus
from .parser import pdf_parser

# 配置日志
logging.basicConfig(
    level=getattr(logging, service_config.log_level),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    logger.info("MinerU服务启动中...")
    logger.info(f"服务地址: {service_config.host}:{service_config.port}")
    logger.info(f"临时目录: {mineru_config.temp_dir}")
    yield
    logger.info("MinerU服务关闭")


# 创建FastAPI应用
app = FastAPI(
    title="MinerU PDF解析服务",
    description="将PDF文档解析为结构化Markdown格式",
    version="1.0.0",
    lifespan=lifespan
)

# CORS配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health", response_model=HealthResponse, tags=["健康检查"])
async def health_check():
    """
    健康检查接口
    """
    return HealthResponse()


@app.post("/parse", response_model=ParseResponse, tags=["PDF解析"])
async def parse_pdf(
    file: UploadFile = File(..., description="PDF文件"),
    enable_ocr: bool = Query(default=False, description="是否启用OCR（扫描件需要）")
):
    """
    解析PDF文件为Markdown格式

    - **file**: PDF文件（multipart/form-data）
    - **enable_ocr**: 是否启用OCR（扫描件PDF需要开启）

    返回：
    - **status**: 解析状态（success/error）
    - **markdown**: Markdown格式的文档内容
    - **title**: 文档标题
    - **total_pages**: 总页数
    - **tables**: 表格列表
    """
    start_time = time.time()

    try:
        # 1. 验证文件类型
        if not file.filename:
            raise HTTPException(status_code=400, detail="文件名为空")

        if not file.filename.lower().endswith('.pdf'):
            raise HTTPException(status_code=400, detail="仅支持PDF文件格式")

        # 2. 读取文件内容
        content = await file.read()

        # 3. 验证文件大小
        file_size_mb = len(content) / (1024 * 1024)
        if file_size_mb > mineru_config.max_file_size_mb:
            raise HTTPException(
                status_code=400,
                detail=f"文件大小超过限制: {file_size_mb:.1f}MB > {mineru_config.max_file_size_mb}MB"
            )

        logger.info(f"收到PDF文件: {file.filename}, 大小: {file_size_mb:.2f}MB")

        # 4. 解析PDF
        markdown, title, total_pages, tables = pdf_parser.parse(content, file.filename)

        # 5. 计算耗时
        elapsed_time = time.time() - start_time
        logger.info(f"解析完成: {file.filename}, 耗时: {elapsed_time:.2f}秒")

        # 6. 返回结果
        return ParseResponse(
            status=ResponseStatus.SUCCESS,
            markdown=markdown,
            title=title,
            total_pages=total_pages,
            tables=tables,
            message=f"解析成功，耗时{elapsed_time:.2f}秒"
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"解析失败: {str(e)}", exc_info=True)
        elapsed_time = time.time() - start_time

        return JSONResponse(
            status_code=500,
            content=ParseResponse(
                status=ResponseStatus.ERROR,
                message=f"解析失败: {str(e)}"
            ).dict()
        )


@app.post("/parse/batch", tags=["PDF解析"])
async def parse_pdf_batch(
    files: list[UploadFile] = File(..., description="PDF文件列表")
):
    """
    批量解析PDF文件（异步任务）

    注意：此接口为预留接口，当前实现为顺序处理
    """
    results = []

    for file in files:
        try:
            content = await file.read()
            markdown, title, total_pages, tables = pdf_parser.parse(content, file.filename)

            results.append({
                "filename": file.filename,
                "status": "success",
                "title": title,
                "total_pages": total_pages
            })
        except Exception as e:
            results.append({
                "filename": file.filename,
                "status": "error",
                "message": str(e)
            })

    return {"results": results}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=service_config.host,
        port=service_config.port,
        workers=service_config.workers,
        log_level=service_config.log_level.lower(),
        reload=service_config.debug
    )
