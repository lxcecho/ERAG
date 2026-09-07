# ============================================================
# 后端本地启动脚本（宿主机运行，覆盖 docker 内部主机名为 localhost）
# 用法：powershell -ExecutionPolicy Bypass -File deploy\start-backend.ps1
# ============================================================
$ErrorActionPreference = "Stop"

$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) { Write-Error ".env not found: $envFile"; exit 1 }

# 1. 读取 .env 设置环境变量（用 $env: 语法确保子进程继承）
$lines = Get-Content $envFile
foreach ($line in $lines) {
    $l = $line.Trim()
    if ($l -and -not $l.StartsWith("#") -and $l.Contains("=")) {
        $idx = $l.IndexOf("=")
        $key = $l.Substring(0, $idx).Trim()
        $val = $l.Substring($idx + 1).Trim()
        Set-Item -Path "Env:$key" -Value $val
    }
}

# 2. 覆盖 docker 内部主机名为 localhost（后端运行在宿主机，非容器内）
$env:DB_HOST = "localhost"
$env:MILVUS_HOST = "localhost"
$env:RABBITMQ_HOST = "localhost"
$env:REDIS_HOST = "localhost"
$env:ES_URIS = "http://localhost:9200"
$env:KB_STORAGE_MINIO_ENDPOINT = "http://localhost:9000"
# Ollama 本地部署，宿主机访问
$env:EMBEDDING_BASE_URL = "http://localhost:11434/v1"

# 3. 显式确保关键变量（防止 .env 解析遗漏）
$env:EMBEDDING_DIMENSION = "1024"
$env:DB_PASSWORD = "root123"
$env:DB_USERNAME = "root"

# 4. 打印关键配置（验证）
Write-Host "=== Backend Startup Config ===" -ForegroundColor Cyan
Write-Host "LLM_BASE_URL      = $env:LLM_BASE_URL"
Write-Host "LLM_MODEL         = $env:LLM_MODEL"
Write-Host "EMBEDDING_BASE_URL= $env:EMBEDDING_BASE_URL"
Write-Host "EMBEDDING_MODEL   = $env:EMBEDDING_MODEL"
Write-Host "EMBEDDING_DIM     = $env:EMBEDDING_DIMENSION"
Write-Host "DB_HOST           = $env:DB_HOST"
Write-Host "DB_PASSWORD       = $env:DB_PASSWORD"
Write-Host "MILVUS_HOST       = $env:MILVUS_HOST"
Write-Host "RABBITMQ_HOST     = $env:RABBITMQ_HOST"
Write-Host "ES_URIS           = $env:ES_URIS"
Write-Host "MINIO_ENDPOINT    = $env:KB_STORAGE_MINIO_ENDPOINT"
Write-Host "==============================" -ForegroundColor Cyan

# 5. 启动 Spring Boot
# JAVA_TOOL_OPTIONS 会被 mvn fork 出的子 JVM 继承，强制 UTF-8，
# 避免 Windows 默认 GBK 导致日志中文乱码、文件名读取异常。
# （不通过 -Dspring-boot.run.jvmArguments 传递，因 PowerShell 对含空格参数转义易出错）
Set-Location "$PSScriptRoot\..\knowledge-backend"
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
# 注意：不加 -am。spring-boot:run 若作用于 reactor 全部模块（含 packaging=pom 的根项目）会报
# "Unable to find a suitable main class"。依赖模块须先 `mvn install -DskipTests` 安装到本地仓库，
# 确保使用源码最新产物（避免残留旧包污染类路径）。
mvn spring-boot:run -pl knowledge-admin
