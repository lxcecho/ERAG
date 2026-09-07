#!/usr/bin/env python
"""
MinerU API测试脚本
"""
import requests
import sys
import os

BASE_URL = os.getenv("MINERU_URL", "http://localhost:8000")


def test_health():
    """测试健康检查"""
    print("测试健康检查接口...")
    try:
        response = requests.get(f"{BASE_URL}/health")
        print(f"状态码: {response.status_code}")
        print(f"响应: {response.json()}")
        return response.status_code == 200
    except Exception as e:
        print(f"错误: {e}")
        return False


def test_parse(pdf_path: str):
    """测试PDF解析"""
    if not os.path.exists(pdf_path):
        print(f"文件不存在: {pdf_path}")
        return False

    print(f"\n测试PDF解析: {pdf_path}")
    print(f"文件大小: {os.path.getsize(pdf_path) / 1024:.2f} KB")

    try:
        with open(pdf_path, 'rb') as f:
            files = {'file': (os.path.basename(pdf_path), f, 'application/pdf')}
            response = requests.post(f"{BASE_URL}/parse", files=files, timeout=300)

        print(f"状态码: {response.status_code}")
        result = response.json()

        if result.get('status') == 'success':
            print(f"标题: {result.get('title')}")
            print(f"页数: {result.get('total_pages')}")
            print(f"表格数: {len(result.get('tables', []))}")
            print(f"Markdown长度: {len(result.get('markdown', ''))} 字符")
            print(f"\nMarkdown前500字符:")
            print("-" * 50)
            print(result.get('markdown', '')[:500])
            print("-" * 50)
        else:
            print(f"解析失败: {result.get('message')}")

        return result.get('status') == 'success'

    except requests.exceptions.Timeout:
        print("错误: 请求超时")
        return False
    except Exception as e:
        print(f"错误: {e}")
        return False


if __name__ == "__main__":
    print("=" * 50)
    print("MinerU API测试")
    print("=" * 50)

    # 测试健康检查
    if not test_health():
        print("\n健康检查失败，请确认服务已启动")
        sys.exit(1)

    # 测试PDF解析
    if len(sys.argv) > 1:
        pdf_path = sys.argv[1]
        test_parse(pdf_path)
    else:
        print("\n提示: 运行 'python test_api.py <pdf_path>' 测试PDF解析")
