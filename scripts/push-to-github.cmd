@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0.."

if "%~1"=="" (
  set /p REPO_URL=Repo URL (https://github.com/<user>/wol-android.git): 
) else (
  set REPO_URL=%~1
)

if "%REPO_URL%"=="" (
  echo [x] Repo URL is required.
  exit /b 1
)

echo.
echo [1/3] remote -> %REPO_URL%
git remote remove origin >nul 2>&1
git remote add origin "%REPO_URL%"

echo [2/3] push main
git push -u origin main
if errorlevel 1 (
  echo.
  echo [!] Push failed.
  echo     A^) Use Git Credential Manager: retry and finish the browser sign-in.
  echo     B^) Use a token once:
  echo        git remote set-url origin https://TOKEN@github.com/USER/wol-android.git
  echo        git push -u origin main
  echo        git remote set-url origin %REPO_URL%
  exit /b 1
)

echo.
echo [ok] Pushed. Check the repo page to confirm the README renders.
endlocal
