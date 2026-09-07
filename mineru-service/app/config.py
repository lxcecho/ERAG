"""
MinerU服务配置
"""
from pydantic import BaseModel
from typing import Optional
import os


class MinerUConfig(BaseModel):
    """MinerU配置"""
    # 是否启用OCR（扫描件PDF需要）
    enable_ocr: bool = False
    # 是否启用表格提取
    enable_table: bool = True
    # 最大文件大小（MB）
    max_file_size_mb: int = 100
    # 临时文件目录
    temp_dir: str = os.getenv("MINERU_TEMP_DIR", "/tmp/mineru")


class ServiceConfig(BaseModel):
    """服务配置"""
    host: str = os.getenv("MINERU_HOST", "0.0.0.0")
    port: int = int(os.getenv("MINERU_PORT", "8000"))
    workers: int = int(os.getenv("MINERU_WORKERS", "1"))
    log_level: str = os.getenv("MINERU_LOG_LEVEL", "INFO")
    # 是否启用调试模式
    debug: bool = os.getenv("MINERU_DEBUG", "false").lower() == "true"


# 全局配置实例
mineru_config = MinerUConfig()
service_config = ServiceConfig()
