@echo off
setlocal
echo ========================================================
echo    Starting DigiAdTechMediaTN Secure Server (HTTPS)
echo ========================================================

cd /d "%~dp0"

echo Compiling Java Server...
javac -encoding UTF-8 DigiAdTechServer.java

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java compilation failed.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Launching DigiAdTechMediaTN Secure Server...
echo   HTTPS: https://localhost:8443/
echo   HTTP:  http://localhost:8080/ (Auto-redirects to HTTPS)
echo.
echo Press Ctrl+C to stop.
echo.

java DigiAdTechServer 8443 8080 changeit

pause
