#!/usr/bin/env python
"""
MinerU服务启动脚本
"""
import uvicorn
from app.config import service_config

if __name__ == "__main__":
    print(f"启动MinerU服务...")
    print(f"地址: http://{service_config.host}:{service_config.port}")
    print(f"文档: http://{service_config.host}:{service_config.port}/docs")

    uvicorn.run(
        "app.main:app",
        host=service_config.host,
        port=service_config.port,
        workers=service_config.workers,
        log_level=service_config.log_level.lower(),
        reload=service_config.debug
    )
