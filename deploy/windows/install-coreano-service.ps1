param(
    [string]$ServiceName = "coreano-api",
    [string]$DeployDir = "C:\apps\coreano-api",
    [string]$AppPort = "8082",
    [string]$ReleaseJar,
    [string]$ReleaseName
)

$ErrorActionPreference = "Stop"

$appJar = Join-Path $DeployDir "app.jar"
$releasesDir = Join-Path $DeployDir "releases"
$uploadsDir = Join-Path $DeployDir "uploads"
$logsDir = Join-Path $DeployDir "logs"

New-Item -ItemType Directory -Force -Path $DeployDir, $releasesDir, $uploadsDir, $logsDir | Out-Null

$service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if (!$service) {
    $principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    if (!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw "Service $ServiceName does not exist and SSH user $env:USERNAME is not an administrator. Create the service once as Administrator or use an administrator SSH credential."
    }
}

if ($ReleaseJar) {
    if (!(Test-Path $ReleaseJar)) {
        throw "Release jar was not found: $ReleaseJar"
    }

    if (!$ReleaseName) {
        $ReleaseName = Split-Path $ReleaseJar -Leaf
    }

    $archivedJar = Join-Path $releasesDir $ReleaseName
    Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
    Copy-Item -Force $ReleaseJar $archivedJar
    Copy-Item -Force $archivedJar $appJar
}

if (!(Test-Path $appJar)) {
    throw "Application jar was not found: $appJar"
}

if ($service) {
    Write-Host "Service $ServiceName already exists."
} else {
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
}

Start-Service -Name $ServiceName
(Get-Service -Name $ServiceName).WaitForStatus(
    [System.ServiceProcess.ServiceControllerStatus]::Running,
    [TimeSpan]::FromSeconds(30)
)
Write-Host "Service $ServiceName is running."
