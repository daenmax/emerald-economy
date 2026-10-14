@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

rem ============================================================
rem  绿宝石经济（Emerald Economy）编译脚本
rem  双击即可运行：调用 gradlew build，并把主 jar 收集到 build\ 目录
rem ============================================================

cd /d "%~dp0"

echo ============================================================
echo   绿宝石经济 - 开始编译
echo ============================================================
echo.

rem 检查 gradlew.bat 是否存在
if not exist "gradlew.bat" (
    echo [错误] 找不到 gradlew.bat，请在项目根目录运行本脚本。
    pause
    exit /b 1
)

echo [1/2] 正在执行 gradlew.bat build ...
echo.
call gradlew.bat build
if errorlevel 1 (
    echo.
    echo ============================================================
    echo   [编译失败] 请查看上方的错误信息。
    echo ============================================================
    pause
    exit /b 1
)

echo.
echo [2/2] 正在收集编译产物 ...

rem 把 build\libs 下的主 jar（排除 -sources / -dev）复制到 build\ 目录
set "OUTDIR=%~dp0build"
if not exist "%OUTDIR%" mkdir "%OUTDIR%"

set "FOUND=0"
for %%F in ("%~dp0build\libs\*.jar") do (
    set "NAME=%%~nxF"
    echo !NAME! | findstr /i /c:"-sources" /c:"-dev" >nul
    if errorlevel 1 (
        copy /y "%%~fF" "%OUTDIR%\" >nul
        echo    已复制: !NAME!
        set "FOUND=1"
    )
)

if "!FOUND!"=="0" (
    echo    [警告] 没有找到主 jar，请检查 build\libs 目录。
)

echo.
echo ============================================================
echo   编译完成！产物目录: %OUTDIR%
echo ============================================================
pause
endlocal
