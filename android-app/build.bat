@echo off
echo ========================================
echo   帮帮农 Android App 构建脚本
echo ========================================
echo.

echo [1/4] 检查 Android SDK 环境...
if "%ANDROID_HOME%"=="" (
    echo 错误: 未设置 ANDROID_HOME 环境变量
    echo.
    echo 请按照以下步骤设置：
    echo 1. 打开系统环境变量设置
    echo 2. 添加 ANDROID_HOME 变量，值为你的 Android SDK 路径
    echo    例如: C:\Users\你的用户名\AppData\Local\Android\Sdk
    echo 3. 将 %%ANDROID_HOME%%\platform-tools 添加到 PATH 变量
    echo.
    pause
    exit /b 1
)
echo Android SDK 路径: %ANDROID_HOME%
echo.

echo [2/4] 同步 Web 资源...
call npx cap sync android
if errorlevel 1 (
    echo 错误: 同步失败
    pause
    exit /b 1
)
echo.

echo [3/4] 构建 Debug APK...
cd android
call gradlew assembleDebug
if errorlevel 1 (
    echo 错误: 构建失败
    echo.
    echo 可能的原因：
    echo 1. Android SDK 未正确安装
    echo 2. Java JDK 版本不兼容
    echo 3. 网络连接问题
    echo.
    echo 请查看上方的错误信息
    pause
    exit /b 1
)
echo.

echo [4/4] 构建完成!
echo.
echo APK 文件位置:
echo android\app\build\outputs\apk\debug\app-debug.apk
echo.
echo 你可以将这个 APK 文件传到手机上安装测试
echo.
pause