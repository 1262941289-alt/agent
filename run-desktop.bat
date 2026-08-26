@echo off
chcp 65001 >nul
setlocal
set "JDK_BIN=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin"
set "DESKTOP=%~dp0desktop"

if not exist "%DESKTOP%\soma\SomaDesktop.class" (
    echo [SOMA] 首次运行，正在编译桌面应用...
    "%JDK_BIN%\javac.exe" -encoding UTF-8 -d "%DESKTOP%" "%DESKTOP%\soma\SomaDesktop.java"
    if errorlevel 1 (
        echo [SOMA] 编译失败，请检查 JDK 路径。
        pause
        exit /b 1
    )
)

echo [SOMA] 启动桌面工作台... （后端 http://localhost:8080）
start "AGENT SOMA Workbench" "%JDK_BIN%\javaw.exe" -cp "%DESKTOP%" soma.SomaDesktop
endlocal