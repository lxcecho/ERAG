"""
数据模型定义
"""
from pydantic import BaseModel, Field
from typing import List, Optional
from enum import Enum


class ResponseStatus(str, Enum):
    """响应状态"""
    SUCCESS = "success"
    ERROR = "error"


class ParseRequest(BaseModel):
    """解析请求"""
    enable_ocr: bool = Field(default=False, description="是否启用OCR")
    enable_table: bool = Field(default=True, description="是否提取表格")


class TableData(BaseModel):
    """表格数据"""
    page_number: int = Field(description="所在页码")
    content: str = Field(description="表格Markdown内容")
    rows: int = Field(default=0, description="行数")
    cols: int = Field(default=0, description="列数")


class ParseResponse(BaseModel):
    """解析响应"""
    status: ResponseStatus = Field(description="响应状态")
    markdown: str = Field(default="", description="Markdown内容")
    title: str = Field(default="", description="文档标题")
    total_pages: int = Field(default=0, description="总页数")
    tables: List[TableData] = Field(default_factory=list, description="表格列表")
    message: Optional[str] = Field(default=None, description="错误信息")


class HealthResponse(BaseModel):
    """健康检查响应"""
    status: str = "ok"
    service: str = "mineru"
    version: str = "0.7.0"
