@echo off
rem Estudely Ark: start the library on this computer (Windows 10 and 11, x64 and ARM64).
rem
rem   Double-click Start-Library.bat         only this computer can use the library
rem   Start-Library.bat --share              other phones/computers on the same Wi-Fi or hotspot can use it too
rem
rem Starts two small programs from Shared-Readers\ and opens START-HERE in your web browser:
rem   kiwix-serve  reads the .zim archives (Wikipedia, guides, docs)   port 8470
rem   miniserve    serves everything else (PDFs, maps, this page)      port 8471
rem Close this window to stop both. Nothing here uses the internet.
rem On Windows ARM64 these x64 programs run under Windows' built-in emulation.

setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "ROOT=%CD%"
if "%LIB_PORT%"=="" set LIB_PORT=8470
if "%FILE_PORT%"=="" set FILE_PORT=8471

set "KIWIX=%ROOT%\Shared-Readers\kiwix-serve\windows-x64\kiwix-serve.exe"
set "MINI=%ROOT%\Shared-Readers\miniserve\windows-x64\miniserve.exe"
if not exist "%KIWIX%" goto missing
if not exist "%MINI%" goto missing

if /i "%~1"=="--share" (
  set KIWIX_ADDR=ipv4
  set FILE_ADDR=0.0.0.0
) else (
  set KIWIX_ADDR=127.0.0.1
  set FILE_ADDR=127.0.0.1
)

rem Files from the internet can be marked "blocked" by Windows. Unblock our own programs only.
powershell -NoProfile -Command "Get-ChildItem -LiteralPath '%ROOT%\Shared-Readers' -Recurse -File -ErrorAction SilentlyContinue | Unblock-File -ErrorAction SilentlyContinue" >nul 2>&1

rem Collect every .zim archive in the modules that were downloaded.
set "ZIMS="
set N=0
for /r "%ROOT%\Core" %%F in (*.zim) do (set ZIMS=!ZIMS! "%%F"& set /a N+=1)
if exist "%ROOT%\Extras" for /r "%ROOT%\Extras" %%F in (*.zim) do (set ZIMS=!ZIMS! "%%F"& set /a N+=1)
if %N%==0 echo No .zim archives found under Core\ or Extras\. Only PDFs and maps will be available.

if not %N%==0 start "kiwix-serve" /b "%KIWIX%" --address=%KIWIX_ADDR% --port=%LIB_PORT% --nodatealiases --blockexternal !ZIMS!
start "miniserve" /b "%MINI%" --interfaces %FILE_ADDR% --port %FILE_PORT% --index START-HERE.html --title "Estudely Ark" --quiet "%ROOT%"

rem Short pause (ping works even without an interactive console, unlike timeout).
ping -n 3 127.0.0.1 >nul
set "URL=http://127.0.0.1:%FILE_PORT%/START-HERE.html?lib=%LIB_PORT%"
echo.
echo   Library is running. Open this in your web browser:  %URL%
if /i "%~1"=="--share" (
  echo   Other devices on the same Wi-Fi can open http://THIS-COMPUTER-IP:%FILE_PORT%/START-HERE.html?lib=%LIB_PORT%
  echo   This computer's addresses:
  for /f "tokens=2 delims=:" %%A in ('ipconfig ^| findstr /c:"IPv4"') do echo      %%A
  echo   If Windows asks whether to allow these programs on networks, choose Allow on private networks.
)
echo   Close this window to stop the library.
echo.
if "%NO_BROWSER%"=="" start "" "%URL%"
rem Keep this window open; closing it stops kiwix-serve and miniserve.
:wait
ping -n 3601 127.0.0.1 >nul
goto wait

:missing
echo Missing the library programs in Shared-Readers\. Download the Shared-Readers folder too.
pause
exit /b 1
