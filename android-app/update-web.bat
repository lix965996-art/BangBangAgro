@echo off
echo ========================================
echo   更新 Web 资源
echo ========================================
echo.

echo [1/3] 构建 Vue 前端项目...
cd ..\vue
call npm run build
if errorlevel 1 (
    echo 错误: Vue 项目构建失败
    pause
    exit /b 1
)
echo.

echo [2/3] 复制构建产物到 www 文件夹...
cd ..\android-app
if exist www rmdir /s /q www
mkdir www
xcopy /E /I /Y ..\vue\dist\* www
if errorlevel 1 (
    echo 错误: 复制文件失败
    pause
    exit /b 1
)
echo.

echo [3/3] 同步到 Android 项目...
call npx cap sync android
if errorlevel 1 (
    echo 错误: 同步失败
    pause
    exit /b 1
)
echo.

echo 更新完成!
echo 现在可以运行 build.bat 来构建新的 APK
echo.
pause