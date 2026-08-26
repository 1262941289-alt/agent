@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
set "JAVA=%JAVA_HOME%\bin\java.exe"
set "JAR=target\sk-agent-1.0.0-SNAPSHOT.jar"
set "AI_BASE_URL=https://api.deepseek.com"
set "AI_MODEL=deepseek-chat"

rem API key loaded from local\secret.env (gitignored, never committed)
if exist "local\secret.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%a in ("local\secret.env") do set "%%a=%%b"
)

echo ============================================
echo  sk-agent backend starter
echo ============================================

if not exist "%JAVA%" (
  echo [ERROR] Java not found: %JAVA%
  exit /b 1
)
if not exist "%JAR%" (
  echo [ERROR] Jar not found: %JAR%
  echo         Build first with: mvn -DskipTests package
  exit /b 1
)
if "%AI_API_KEY%"=="" (
  echo [WARN] AI_API_KEY is empty. Put a line into local\secret.env: AI_API_KEY=sk-xxxx
)

rem Already running check (port 8080)
netstat -ano | findstr "LISTENING" | findstr ":8080 " >nul 2>&1
if %errorlevel%==0 (
  echo [INFO] Port 8080 already in use - backend is running, nothing to do.
  echo        Open http://localhost:8080/run.html
  exit /b 0
)

rem Dependency check: MySQL on 3306 (warn only, never auto-start system services)
netstat -ano | findstr "LISTENING" | findstr ":3306 " >nul 2>&1
if not %errorlevel%==0 (
  echo [WARN] MySQL 3306 not listening - app will fail to connect to database.
  echo        Start MySQL first, e.g.:
  echo        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe" --datadir=C:\Users\12629\mysql-data
)

echo [START] java -Djdk.net.hosts.file="%~dp0local\dns-override.hosts" -jar %JAR%
start "sk-agent" /min "%JAVA%" "-Djdk.net.hosts.file=%~dp0local\dns-override.hosts" -jar "%JAR%"

echo [DONE] Launched in background window (first start takes 10-20s).
echo        Console:  http://localhost:8080/
echo        Run page:  http://localhost:8080/run.html
endlocal
