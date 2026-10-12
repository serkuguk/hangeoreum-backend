param(
    [string]$ServiceName = "coreano-api",
    [string]$DeployDir = "C:\apps\coreano-api",
    [string]$AppPort = "8082",
    [string]$EnvFile,
    [string]$DatasourceUrl,
    [string]$DatasourceUsername,
    [string]$DatasourcePassword,
    [string]$JwtSecret,
    [string]$FrontOrigin,
    [string]$FrontUrl,
    [string]$StripeSecretKey,
    [string]$StripeWebhookSecret,
    [string]$ReleaseJar,
    [string]$ReleaseName,
    [string]$DeployUser = "jenkins"
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

$nssm = (Get-Command nssm.exe -ErrorAction SilentlyContinue).Source
if (!$nssm) {
    $nssm = "C:\tools\nssm\nssm.exe"
}

if (!(Test-Path $nssm)) {
    throw "nssm.exe was not found. Install NSSM and make it available in PATH or at C:\tools\nssm\nssm.exe."
}

$envValues = @{}
if ($EnvFile) {
    if (!(Test-Path $EnvFile)) {
        throw "Environment file was not found: $EnvFile"
    }

    Get-Content $EnvFile | ForEach-Object {
        if ($_ -and $_ -notmatch '^\s*#') {
            $name, $value = $_ -split '=', 2
            if ($name) {
                $envValues[$name.Trim()] = $value
            }
        }
    }
}

if ($DatasourceUrl) { $envValues["SPRING_DATASOURCE_URL"] = $DatasourceUrl }
if ($DatasourceUsername) { $envValues["SPRING_DATASOURCE_USERNAME"] = $DatasourceUsername }
if ($DatasourcePassword) { $envValues["SPRING_DATASOURCE_PASSWORD"] = $DatasourcePassword }
if ($JwtSecret) { $envValues["JWT_SECRET"] = $JwtSecret }
if ($FrontOrigin) { $envValues["FRONT_ORIGIN"] = $FrontOrigin }
if ($FrontUrl) { $envValues["FRONT_URL"] = $FrontUrl }
if ($StripeSecretKey) { $envValues["STRIPE_SECRET_KEY"] = $StripeSecretKey }
if ($StripeWebhookSecret) { $envValues["STRIPE_WEBHOOK_SECRET"] = $StripeWebhookSecret }

$serviceEnv = @(
    "SERVER_PORT=$AppPort",
    "MEDIA_DIR=$uploadsDir"
)

foreach ($name in $envValues.Keys) {
    $serviceEnv += "$name=$($envValues[$name])"
}

if ($service) {
    Write-Host "Service $ServiceName already exists."
} else {
    $java = (Get-Command java.exe -ErrorAction SilentlyContinue).Source
    if (!$java) {
        throw "java.exe was not found in PATH. Install Java 21 and add it to PATH."
    }

    & $nssm install $ServiceName $java "-jar `"$appJar`""
    & $nssm set $ServiceName AppDirectory $DeployDir
    & $nssm set $ServiceName AppStdout (Join-Path $logsDir "stdout.log")
    & $nssm set $ServiceName AppStderr (Join-Path $logsDir "stderr.log")
    & $nssm set $ServiceName AppRotateFiles 1
    & $nssm set $ServiceName AppRotateOnline 1
    & $nssm set $ServiceName Start SERVICE_AUTO_START

    $deploySid = [Security.Principal.NTAccount]::new($env:COMPUTERNAME, $DeployUser).
        Translate([Security.Principal.SecurityIdentifier]).Value
    $serviceSddl = (& sc.exe sdshow $ServiceName | Where-Object { $_ -like "D:*" } | Select-Object -First 1)
    if (!$serviceSddl) {
        throw "Could not read the security descriptor for service $ServiceName."
    }

    $serviceAce = "(A;;CCLCSWRPWPDTLOCRRC;;;$deploySid)"
    if ($serviceSddl -notlike "*$deploySid*") {
        $serviceSddl = if ($serviceSddl -match "^(.*?)(S:.*)$") {
            "$($Matches[1])$serviceAce$($Matches[2])"
        } else {
            "$serviceSddl$serviceAce"
        }

        & sc.exe sdset $ServiceName $serviceSddl | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Could not grant $DeployUser control of service $ServiceName."
        }
    }

    Write-Host "Service $ServiceName was created."
}

& $nssm set $ServiceName AppEnvironmentExtra $serviceEnv
Start-Service -Name $ServiceName
(Get-Service -Name $ServiceName).WaitForStatus(
    [System.ServiceProcess.ServiceControllerStatus]::Running,
    [TimeSpan]::FromSeconds(30)
)
Write-Host "Service $ServiceName is running."
