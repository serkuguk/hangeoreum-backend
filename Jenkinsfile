pipeline {
    agent { label 'windows' }

    options {
        timestamps()
    }

    environment {
        APP_NAME = 'coreano-api'
        APP_PORT = '8082'
        DEPLOY_DIR = 'C:/apps/coreano-api'
        SERVICE_NAME = 'coreano-api'
        GIT_REPO = 'https://github.com/serkuguk/hangeoreum-backend.git'
        WINDOWS_HOST = '192.168.10.96'
        WINDOWS_SSH_PORT = '2222'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: env.GIT_REPO
            }
        }

        stage('Build and Test') {
            steps {
                bat 'call mvnw.cmd clean package'
            }
        }

        stage('Find Artifact') {
            steps {
                script {
                    env.APP_JAR = powershell(
                        script: '''(Get-ChildItem -Path target -Filter *.jar | Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1).FullName''',
                        returnStdout: true
                    ).trim()

                    if (!env.APP_JAR) {
                        error('Built jar was not found in target/')
                    }
                }
            }
        }

        stage('Deploy to Windows Server') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'node-windows-ssh',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {
                    powershell '''
                        $ErrorActionPreference = 'Stop'
                        icacls $env:SSH_KEY /inheritance:r /grant:r "$($env:USERNAME):(R)" | Out-Null

                        $sshTarget = "$($env:SSH_USER)@$($env:WINDOWS_HOST)"
                        $sshOpts = @(
                            '-i', $env:SSH_KEY,
                            '-p', $env:WINDOWS_SSH_PORT,
                            '-o', 'IdentitiesOnly=yes',
                            '-o', 'HostKeyAlgorithms=ssh-ed25519',
                            '-o', 'StrictHostKeyChecking=yes',
                            '-o', "UserKnownHostsFile=$env:WORKSPACE/deploy/windows/known_hosts"
                        )
                        $scpOpts = @(
                            '-i', $env:SSH_KEY,
                            '-P', $env:WINDOWS_SSH_PORT,
                            '-o', 'IdentitiesOnly=yes',
                            '-o', 'HostKeyAlgorithms=ssh-ed25519',
                            '-o', 'StrictHostKeyChecking=yes',
                            '-o', "UserKnownHostsFile=$env:WORKSPACE/deploy/windows/known_hosts"
                        )
                        $releaseJar = "$($env:DEPLOY_DIR)/releases/$($env:APP_NAME)-$($env:BUILD_NUMBER).jar"

                        $ErrorActionPreference = 'Continue'
                        ssh @sshOpts $sshTarget "powershell -NoProfile -ExecutionPolicy Bypass -Command `"New-Item -ItemType Directory -Force -Path '$env:DEPLOY_DIR','$env:DEPLOY_DIR/releases','$env:DEPLOY_DIR/uploads','$env:DEPLOY_DIR/scripts' | Out-Null`""
                        if ($LASTEXITCODE) { exit $LASTEXITCODE }
                        scp @scpOpts $env:APP_JAR "$sshTarget`:$releaseJar"
                        if ($LASTEXITCODE) { exit $LASTEXITCODE }
                        scp @scpOpts "deploy/windows/install-coreano-service.ps1" "$sshTarget`:$($env:DEPLOY_DIR)/scripts/install-coreano-service.ps1"
                        if ($LASTEXITCODE) { exit $LASTEXITCODE }
                        ssh @sshOpts $sshTarget "powershell -NoProfile -ExecutionPolicy Bypass -Command `"`$ErrorActionPreference = 'Stop'; Stop-Service -Name '$env:SERVICE_NAME' -ErrorAction SilentlyContinue; Copy-Item -Force '$releaseJar' '$env:DEPLOY_DIR/app.jar'; & '$env:DEPLOY_DIR/scripts/install-coreano-service.ps1' -ServiceName '$env:SERVICE_NAME' -DeployDir '$env:DEPLOY_DIR' -AppPort '$env:APP_PORT'; Start-Service -Name '$env:SERVICE_NAME'; Start-Sleep -Seconds 10; if ((Get-Service -Name '$env:SERVICE_NAME').Status -ne 'Running') { throw 'Service $env:SERVICE_NAME is not running' }`""
                        if ($LASTEXITCODE) { exit $LASTEXITCODE }
                    '''
                }
            }
        }

        stage('Health Check') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'node-windows-ssh',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {
                    powershell '''
                        $ErrorActionPreference = 'Stop'
                        icacls $env:SSH_KEY /inheritance:r /grant:r "$($env:USERNAME):(R)" | Out-Null

                        $sshTarget = "$($env:SSH_USER)@$($env:WINDOWS_HOST)"
                        $sshOpts = @(
                            '-i', $env:SSH_KEY,
                            '-p', $env:WINDOWS_SSH_PORT,
                            '-o', 'IdentitiesOnly=yes',
                            '-o', 'HostKeyAlgorithms=ssh-ed25519',
                            '-o', 'StrictHostKeyChecking=yes',
                            '-o', "UserKnownHostsFile=$env:WORKSPACE/deploy/windows/known_hosts"
                        )

                        $ErrorActionPreference = 'Continue'
                        ssh @sshOpts $sshTarget "powershell -NoProfile -ExecutionPolicy Bypass -Command `"Invoke-RestMethod -Uri 'http://localhost:$env:APP_PORT/actuator/health' -TimeoutSec 20 | ConvertTo-Json -Compress`""
                        if ($LASTEXITCODE) { exit $LASTEXITCODE }
                    '''
                }
            }
        }
    }
}
