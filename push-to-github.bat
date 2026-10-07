@echo off
setlocal
echo ========================================================
echo    Pushing DigiAdTechMediaTN Website to GitHub
echo ========================================================
echo.

cd /d "%~dp0"

echo Repository: https://github.com/dineshd56073-spec/digiadtechmediatn.git
echo.
echo Running git push...
echo (If a browser window opens, click "Sign in with your browser")
echo.

git push -u origin main

if %ERRORLEVEL% equ 0 (
    echo.
    echo ========================================================
    echo  SUCCESS! Your code has been uploaded to GitHub!
    echo ========================================================
    echo.
    echo Next step:
    echo 1. Go to https://github.com/dineshd56073-spec/digiadtechmediatn/settings/pages
    echo 2. Under Branch, select 'main' and click Save.
    echo 3. Your website will be live in 1 minute!
    echo.
) else (
    echo.
    echo [Notice] If authentication was cancelled, please run this again and complete the browser login.
)

pause
