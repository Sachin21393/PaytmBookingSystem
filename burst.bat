@echo off
REM Paytm Money Take-Home Assignment - Windows Burst Runner
REM Usage: burst.bat [TARGET_URL] [TOTAL] [CONCURRENCY]
REM Example: burst.bat https://paytmbookingsystem.onrender.com

set TARGET_URL=%1
if "%TARGET_URL%"=="" set TARGET_URL=https://paytmbookingsystem.onrender.com

set TOTAL=%2
if "%TOTAL%"=="" set TOTAL=20000

set CONCURRENCY=%3
if "%CONCURRENCY%"=="" set CONCURRENCY=100

echo ======================================================================
echo  PAYTM MONEY LOAD TEST RUNNER (WINDOWS)
echo  Target URL   : %TARGET_URL%
echo  Total Bursts : %TOTAL% requests
echo  Concurrency  : %CONCURRENCY% parallel workers
echo ======================================================================

where node >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: Node.js is required to run the load test.
    echo Please install Node.js 18+ and try again.
    exit /b 1
)

node burst_test.js %TARGET_URL% --total %TOTAL% --concurrency %CONCURRENCY%
