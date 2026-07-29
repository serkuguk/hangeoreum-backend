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
        WINDOWS_HOST = 'host.docker.internal'
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

                        $sshTarget = "$($env:SSH_USER)@$($env:WINDOWS_HOST)"
                        $sshOpts = @(
                            '-i', $env:SSH_KEY,
                            '-p', $env:WINDOWS_SSH_PORT,
                            '-o', 'IdentitiesOnly=yes',
                            '-o', 'StrictHostKeyChecking=yes',
                            '-o', "UserKnownHostsFile=$env:WORKSPACE/deploy/windows/known_hosts"
                        )
                        $scpOpts = @(
                            '-i', $env:SSH_KEY,
                            '-P', $env:WINDOWS_SSH_PORT,
                            '-o', 'IdentitiesOnly=yes',
                            '-o', 'StrictHostKeyChecking=yes',
                            '-o', "UserKnownHostsFile=$env:WORKSPACE/deploy/windows/known_hosts"
                        )
                        $releaseJar = "$($env:DEPLOY_DIR)/releases/$($env:APP_NAME)-$($env:BUILD_NUMBER).jar"

                        ssh @sshOpts $sshTarget "powershell -NoProfile -ExecutionPolicy Bypass -Command `"New-Item -ItemType Directory -Force -Path '$env:DEPLOY_DIR','$env:DEPLOY_DIR/releases','$env:DEPLOY_DIR/uploads','$env:DEPLOY_DIR/scripts' | Out-Null`""
                        scp @scpOpts $env:APP_JAR "$sshTarget`:$releaseJar"
                        scp @scpOpts "deploy/windows/install-coreano-service.ps1" "$sshTarget`:$($env:DEPLOY_DIR)/scripts/install-coreano-service.ps1"
                        ssh @sshOpts $sshTarget "powershell -NoProfile -ExecutionPolicy Bypass -Command `"`$ErrorActionPreference = 'Stop'; Stop-Service -Name '$env:SERVICE_NAME' -ErrorAction SilentlyContinue; Copy-Item -Force '$releaseJar' '$env:DEPLOY_DIR/app.jar'; & '$env:DEPLOY_DIR/scripts/install-coreano-service.ps1' -ServiceName '$env:SERVICE_NAME' -DeployDir '$env:DEPLOY_DIR' -AppPort '$env:APP_PORT'; Start-Service -Name '$env:SERVICE_NAME'; Start-Sleep -Seconds 10; if ((Get-Service -Name '$env:SERVICE_NAME').Status -ne 'Running') { throw 'Service $env:SERVICE_NAME is not running' }`""
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

                        $sshTarget = "$($env:SSH_USER)@$($env:WINDOWS_HOST)"
                        $sshOpts = @(
                            '-i', $env:SSH_KEY,
                            '-p', $env:WINDOWS_SSH_PORT,
                            '-o', 'IdentitiesOnly=yes',
                            '-o', 'StrictHostKeyChecking=yes',
                            '-o', "UserKnownHostsFile=$env:WORKSPACE/deploy/windows/known_hosts"
                        )

                        ssh @sshOpts $sshTarget "powershell -NoProfile -ExecutionPolicy Bypass -Command `"Invoke-RestMethod -Uri 'http://localhost:$env:APP_PORT/actuator/health' -TimeoutSec 20 | ConvertTo-Json -Compress`""
                    '''
                }
            }
        }
    }
}
