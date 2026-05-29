@echo off
chcp 65001 >nul
echo ============================================
echo 果蔬双检分析 API 服务启动脚本
echo ============================================
echo.

cd /d "%~dp0"

echo [1/2] 检查 Python 环境...
python --version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Python，请先安装 Python 3.7+
    pause
    exit /b 1
)
python --version
echo.

echo [2/2] 启动 AI 检测服务...
echo.
python integrated_api_server.py
