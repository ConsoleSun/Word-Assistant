@echo off
set JAVA_HOME=D:\jdk17\jdk-17.0.9+8
set GRADLE_HOME=D:\gradle
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
    echo Gradle not found, extracting...
    if not exist "D:\gradle.zip" (
        echo Please download gradle-8.4-bin.zip to D:\
        pause
        exit /b 1
    )
    powershell -Command "Expand-Archive -Force D:\gradle.zip D:\gradle-tmp"
    move D:\gradle-tmp\gradle-* D:\gradle > nul 2>&1
    rmdir D:\gradle-tmp 2>nul
)
"%GRADLE_HOME%\bin\gradle.bat" %*
