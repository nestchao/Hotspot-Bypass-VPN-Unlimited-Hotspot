@echo off
setlocal
cd /d "%~dp0"

where python >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python is not on PATH. Install Python 3 and retry.
    exit /b 1
)
if not exist "venv\Scripts\python.exe" (
    python -m venv venv
    if errorlevel 1 exit /b 1
)

"venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 exit /b 1

"venv\Scripts\python.exe" -m PyInstaller --clean --noconfirm LaptopProxy.spec
if errorlevel 1 exit /b 1

powershell -NoProfile -Command "Compress-Archive -LiteralPath 'dist\Hotspot_Bypass_VPN_Windows' -DestinationPath 'dist\Hotspot_Bypass_VPN_Windows_Portable.zip' -Force"
if errorlevel 1 exit /b 1

echo Portable build: dist\Hotspot_Bypass_VPN_Windows_Portable.zip
certutil -hashfile "dist\Hotspot_Bypass_VPN_Windows_Portable.zip" SHA256
if errorlevel 1 exit /b 1
exit /b 0
