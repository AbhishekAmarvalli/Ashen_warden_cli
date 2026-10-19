@echo off
rem =====================================================================
rem  ASHEN WARDEN: THE LAST EMBER — Windows CMD launcher
rem  Usage:  play.bat            (double-click or type in CMD)
rem  Flags pass through, e.g.:   play.bat --plain --width 100
rem =====================================================================
setlocal

rem UTF-8 console so the game's borders, dashes and symbols display right
chcp 65001 >nul

rem Always run from the project folder (where this .bat lives)
cd /d "%~dp0"

rem --- Preflight checks with friendly messages (no stack traces) --------
where java >nul 2>nul
if errorlevel 1 (
    echo.
    echo  [ERROR] Java was not found on your PATH.
    echo          Install JDK 17 or newer, then open a NEW cmd window.
    echo.
    pause
    exit /b 1
)
if not exist "target\ashen-warden.jar" (
    echo.
    echo  [ERROR] target\ashen-warden.jar not found.
    echo          Build it first:   mvn package
    echo.
    pause
    exit /b 1
)

rem --- MySQL credentials ------------------------------------------------
rem The game uses DB_USER / DB_PASSWORD if you set them in Windows first
rem (setx DB_USER ashen  /  setx DB_PASSWORD your-password).
rem Defaults below are a local-dev convenience — EDIT THEM if yours differ.
if not defined DB_USER set "DB_USER=root"
if not defined DB_PASSWORD set "DB_PASSWORD=root"

rem UTF-8 file encoding flags keep Windows terminals readable
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "target\ashen-warden.jar" %*

set "EXITCODE=%ERRORLEVEL%"
if not "%EXITCODE%"=="0" (
    echo.
    echo  [ERROR] The game exited with code %EXITCODE%.
    echo          Please report this window's output.
    echo.
    pause
)
endlocal & exit /b %EXITCODE%
