@echo off
setlocal EnableExtensions
set "GRADLE_VERSION=8.13"
set "GRADLE_HOME_CUSTOM=%USERPROFILE%\.gradle\retroccd\gradle-%GRADLE_VERSION%"
set "GRADLE_ZIP=%USERPROFILE%\.gradle\retroccd\gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_HOME_CUSTOM%\bin\gradle.bat" goto run_gradle
if not exist "%USERPROFILE%\.gradle\retroccd" mkdir "%USERPROFILE%\.gradle\retroccd"

echo [RetroCCD] Downloading Gradle %GRADLE_VERSION% (one-time setup)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'"
if errorlevel 1 (
  echo [ERROR] Gradle download failed. Check your internet connection.
  exit /b 1
)

echo [RetroCCD] Extracting Gradle...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%GRADLE_ZIP%' -DestinationPath '%USERPROFILE%\.gradle\retroccd' -Force"
if errorlevel 1 (
  echo [ERROR] Could not extract Gradle.
  exit /b 1
)

:run_gradle
call "%GRADLE_HOME_CUSTOM%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
