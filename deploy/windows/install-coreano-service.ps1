param(
    [string]$ServiceName = "coreano-api",
    [string]$DeployDir = "C:\apps\coreano-api",
    [string]$AppPort = "8082"
)

$ErrorActionPreference = "Stop"

$appJar = Join-Path $DeployDir "app.jar"
$uploadsDir = Join-Path $DeployDir "uploads"
$logsDir = Join-Path $DeployDir "logs"

New-Item -ItemType Directory -Force -Path $DeployDir, $uploadsDir, $logsDir | Out-Null

if (!(Test-Path $appJar)) {
    throw "Application jar was not found: $appJar"
}

$service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($service) {
    Write-Host "Service $ServiceName already exists."
    exit 0
}

$java = (Get-Command java.exe -ErrorAction SilentlyContinue).Source
if (!$java) {
    throw "java.exe was not found in PATH. Install Java 21 and add it to PATH."
}

$nssm = (Get-Command nssm.exe -ErrorAction SilentlyContinue).Source
if (!$nssm) {
    $nssm = "C:\tools\nssm\nssm.exe"
}

if (!(Test-Path $nssm)) {
    throw "nssm.exe was not found. Install NSSM and make it available in PATH or at C:\tools\nssm\nssm.exe."
}

& $nssm install $ServiceName $java "-jar `"$appJar`""
& $nssm set $ServiceName AppDirectory $DeployDir
& $nssm set $ServiceName AppStdout (Join-Path $logsDir "stdout.log")
& $nssm set $ServiceName AppStderr (Join-Path $logsDir "stderr.log")
& $nssm set $ServiceName AppRotateFiles 1
& $nssm set $ServiceName AppRotateOnline 1
& $nssm set $ServiceName AppEnvironmentExtra "SERVER_PORT=$AppPort" "MEDIA_DIR=$uploadsDir"
& $nssm set $ServiceName Start SERVICE_AUTO_START

Write-Host "Service $ServiceName was created."
