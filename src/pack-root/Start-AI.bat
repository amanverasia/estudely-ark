@echo off
rem Estudely Ark: start a local AI assistant (Windows 10 and 11, x64 and ARM64). Runs entirely on this computer.
rem
rem   Double-click Start-AI.bat       small model (Gemma 4 E4B), runs on the processor
rem   Start-AI.bat 12b                larger model (Gemma 4 12B)
rem   Start-AI.bat e4b gpu            use the graphics card through Vulkan (faster on many PCs; try it)
rem
rem Then open http://127.0.0.1:8472 in your web browser. Close this window to stop.
rem The AI can be wrong. For health or safety questions, check the guides in the library.

setlocal
cd /d "%~dp0"
set "ROOT=%CD%"

set ARCH=x64
if /i "%PROCESSOR_ARCHITECTURE%"=="ARM64" set ARCH=arm64
if /i "%PROCESSOR_ARCHITEW6432%"=="ARM64" set ARCH=arm64
set KIND=cpu
if /i "%~2"=="gpu" set KIND=vulkan
if /i "%~1"=="gpu" set KIND=vulkan

set "DIR=%ROOT%\AI\Gemma-4-E4B"
set "MODEL=gemma-4-E4B_q4_0-it.gguf"
set "MMPROJ=gemma-4-E4B-it-mmproj.gguf"
if /i "%~1"=="12b" (
  set "DIR=%ROOT%\AI\Gemma-4-12B"
  set "MODEL=gemma-4-12b-it-qat-q4_0.gguf"
  set "MMPROJ=mmproj-gemma-4-12b-it-qat-q4_0.gguf"
)
if not exist "%DIR%\%MODEL%" (
  echo Model not found: %DIR%\%MODEL%
  echo Download that AI folder first.
  pause
  exit /b 1
)

set "RUNNER=%ROOT%\AI\Runner-and-Instructions\llama.cpp\windows-%KIND%-%ARCH%"
if not exist "%RUNNER%\llama-server.exe" (
  echo AI runner not found: %RUNNER%
  pause
  exit /b 1
)
powershell -NoProfile -Command "Get-ChildItem -LiteralPath '%RUNNER%' -Recurse -File | Unblock-File -ErrorAction SilentlyContinue" >nul 2>&1

set "MM="
if exist "%DIR%\%MMPROJ%" set MM=--mmproj "%DIR%\%MMPROJ%"
if "%AI_PORT%"=="" set AI_PORT=8472
if "%AI_HOST%"=="" set AI_HOST=127.0.0.1
if "%AI_CTX%"=="" set AI_CTX=8192
if "%AI_THINK%"=="" set AI_THINK=off

echo Starting the AI (%KIND%, %ARCH%). The first start can take a minute while the model loads.
echo When you see "server is listening", open http://127.0.0.1:%AI_PORT% in your web browser.
echo.
cd /d "%RUNNER%"
llama-server.exe --offline -m "%DIR%\%MODEL%" %MM% --host %AI_HOST% --port %AI_PORT% -c %AI_CTX% --fit on --reasoning %AI_THINK% %AI_ARGS%
echo.
echo The AI stopped. If it closed straight away with "gpu", try again without "gpu".
pause
