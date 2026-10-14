@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem  绿宝石经济（Emerald Economy）清理脚本
rem  双击即可运行：执行 gradlew clean，并删除 build / run / logs
rem ============================================================

cd /d "%~dp0"

echo ============================================================
echo   绿宝石经济 - 开始清理
echo ============================================================
echo.

if not exist "gradlew.bat" (
    echo [错误] 找不到 gradlew.bat，请在项目根目录运行本脚本。
    pause
    exit /b 1
)

echo [1/2] 正在执行 gradlew.bat clean ...
call gradlew.bat clean
if errorlevel 1 (
    echo.
    echo [警告] gradlew clean 执行失败，将继续删除本地目录。
)

echo.
echo [2/2] 正在删除 build\ / run\ / logs\ ...

rem 注意：只删除编译产物与运行目录，不动 gradle、src、gradlew 等必要文件
for %%D in ("build" "run" "logs") do (
    if exist "%%~D" (
        echo    删除目录: %%~D
        rmdir /s /q "%%~D"
    ) else (
        echo    跳过（不存在）: %%~D
    )
)

echo.
echo ============================================================
echo   清理完成！
echo ============================================================
pause
endlocal
