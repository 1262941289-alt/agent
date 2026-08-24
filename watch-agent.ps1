# watch-agent.ps1 - sk-agent backend watchdog
# Single check-and-fix run; a scheduled task invokes this every minute.
# Healthy = silent exit 0. Actions/errors are appended to logs\watchdog.log.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$bat = Join-Path $root 'start-agent.bat'
$logDir = Join-Path $root 'logs'
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Force $logDir | Out-Null }
$log = Join-Path $logDir 'watchdog.log'

function Log($msg) {
  "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')  $msg" | Add-Content -Path $log -Encoding UTF8
}

try {
  $listening = netstat -ano | Select-String 'LISTENING' | Select-String ':8080 '
  if ($listening) { exit 0 }

  Log 'port 8080 not listening -> invoking start-agent.bat'
  & cmd /c "`"$bat`"" *> $null
  Start-Sleep -Seconds 5

  $listening = netstat -ano | Select-String 'LISTENING' | Select-String ':8080 '
  if ($listening) {
    Log 'backend started by watchdog'
  } else {
    Log 'WARN: start attempted, port still not listening after 5s (app may still be booting)'
  }
} catch {
  Log "ERROR: $($_.Exception.Message)"
}
