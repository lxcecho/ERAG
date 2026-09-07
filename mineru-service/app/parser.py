"""
PDF解析核心逻辑
"""
import logging
import tempfile
import os
import re
from typing import Tuple, List
from pathlib import Path

from .config import mineru_config
from .models import TableData

logger = logging.getLogger(__name__)


class PDFParser:
    """PDF解析器"""

    def __init__(self):
        self.temp_dir = mineru_config.temp_dir
        os.makedirs(self.temp_dir, exist_ok=True)

    def parse(self, pdf_bytes: bytes, filename: str = "document.pdf") -> Tuple[str, str, int, List[TableData]]:
        """
        解析PDF文件

        Args:
            pdf_bytes: PDF文件字节内容
            filename: 文件名

        Returns:
            Tuple[markdown, title, total_pages, tables]
        """
        logger.info(f"开始解析PDF: {filename}, 大小: {len(pdf_bytes)} bytes")

        try:
            # 使用MinerU解析
            markdown = self._parse_with_mineru(pdf_bytes)

            # 提取标题
            title = self._extract_title(markdown)

            # 估算页数
            total_pages = self._estimate_pages(markdown)

            # 提取表格（MinerU已经在Markdown中保留了表格格式）
            tables = self._extract_tables_info(markdown)

            logger.info(f"解析完成: 标题={title}, 页数={total_pages}, 表格数={len(tables)}")

            return markdown, title, total_pages, tables

        except Exception as e:
            logger.error(f"解析失败: {str(e)}", exc_info=True)
            raise

    def _parse_with_mineru(self, pdf_bytes: bytes) -> str:
        """使用MinerU解析PDF"""
        try:
            from magic_pdf.pipe.UNIPipe import UNIPipe

            # 创建MinerU管道
            pipe = UNIPipe(
                pdf_bytes,
                [],  # 图片列表（可选）
                [],  # 表格列表（可选）
                is_debug=False
            )

            # 执行解析
            pipe.pipe_parse()

            # 获取Markdown输出
            markdown = pipe.get_markdown()

            if not markdown:
                raise ValueError("MinerU返回空内容")

            return markdown

        except ImportError:
            logger.error("MinerU未安装，请执行: pip install magic-pdf[full]")
            raise
        except Exception as e:
            logger.error(f"MinerU解析失败: {str(e)}")
            raise

    def _extract_title(self, markdown: str) -> str:
        """从Markdown中提取文档标题"""
        if not markdown:
            return "未知文档"

        lines = markdown.split("\n")

        # 1. 查找一级标题
        for line in lines:
            line = line.strip()
            if line.startswith("# "):
                return line[2:].strip()

        # 2. 查找二级标题
        for line in lines:
            line = line.strip()
            if line.startswith("## "):
                return line[3:].strip()

        # 3. 返回第一行非空文本
        for line in lines:
            line = line.strip()
            if line and not line.startswith("#") and len(line) > 2:
                # 截取前50个字符
                return line[:50] + ("..." if len(line) > 50 else "")

        return "未知文档"

    def _estimate_pages(self, markdown: str) -> int:
        """估算PDF页数"""
        if not markdown:
            return 0

        # 简单估算：每页约2000字符
        # 更精确的方法是统计页面分隔符或图片数量
        char_count = len(markdown)
        estimated_pages = max(1, char_count // 2000)

        return estimated_pages

    def _extract_tables_info(self, markdown: str) -> List[TableData]:
        """从Markdown中提取表格信息"""
        tables = []

        if not markdown:
            return tables

        # 匹配Markdown表格
        table_pattern = re.compile(r'(\|.+\|[\n\r]+\|[-| :]+\|[\n\r]+(?:\|.+\|[\n\r]*)+)', re.MULTILINE)

        for match in table_pattern.finditer(markdown):
            table_content = match.group(1)

            # 统计行列数
            lines = [l.strip() for l in table_content.split('\n') if l.strip()]
            if len(lines) < 3:  # 至少需要表头、分隔行、数据行
                continue

            # 解析表头
            headers = [cell.strip() for cell in lines[0].split('|') if cell.strip()]
            cols = len(headers)

            # 数据行数（减去表头和分隔行）
            rows = len(lines) - 2

            tables.append(TableData(
                page_number=0,  # 无法从Markdown确定具体页码
                content=table_content,
                rows=rows,
                cols=cols
            ))

        return tables


# 全局解析器实例
pdf_parser = PDFParser()
