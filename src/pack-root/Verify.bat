@echo off
rem Estudely Ark: check that this copy is genuine and undamaged (Windows). See Verify.ps1 for details.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Verify.ps1"
echo.
pause
