pipeline {
    agent any

    options {
        timestamps()
    }

    environment {
        APP_NAME = 'coreano-api'
        APP_PORT = '8082'
        DEPLOY_DIR = 'C:/apps/coreano-api'
        SERVICE_NAME = 'coreano-api'
        GIT_REPO = 'https://github.com/serkuguk/hangeoreum-backend.git'
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
                        credentialsId: 'coreano-windows-ssh-key',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    ),
                    file(credentialsId: 'coreano-windows-known-hosts', variable: 'KNOWN_HOSTS'),
                    string(credentialsId: 'coreano-windows-host', variable: 'WINDOWS_HOST')
                ]) {
                    powershell '''
                        $ErrorActionPreference = 'Stop'

                        $sshTarget = "$($env:SSH_USER)@$($env:WINDOWS_HOST)"
                        $sshOpts = @(
                            '-i', $env:SSH_KEY,
                            '-o', 'IdentitiesOnly=yes',
                            '-o', 'StrictHostKeyChecking=yes',
                            '-o', "UserKnownHostsFile=$env:KNOWN_HOSTS"
                        )
                        $releaseJar = "$($env:DEPLOY_DIR)/releases/$($env:APP_NAME)-$($env:BUILD_NUMBER).jar"

                        ssh @sshOpts $sshTarget "powershell -NoProfile -ExecutionPolicy Bypass -Command `"New-Item -ItemType Directory -Force -Path '$env:DEPLOY_DIR','$env:DEPLOY_DIR/releases','$env:DEPLOY_DIR/uploads','$env:DEPLOY_DIR/scripts' | Out-Null`""
                        scp @sshOpts $env:APP_JAR "$sshTarget`:$releaseJar"
                        scp @sshOpts "deploy/windows/install-coreano-service.ps1" "$sshTarget`:$($env:DEPLOY_DIR)/scripts/install-coreano-service.ps1"
                        ssh @sshOpts $sshTarget "powershell -NoProfile -ExecutionPolicy Bypass -Command `"`$ErrorActionPreference = 'Stop'; Stop-Service -Name '$env:SERVICE_NAME' -ErrorAction SilentlyContinue; Copy-Item -Force '$releaseJar' '$env:DEPLOY_DIR/app.jar'; & '$env:DEPLOY_DIR/scripts/install-coreano-service.ps1' -ServiceName '$env:SERVICE_NAME' -DeployDir '$env:DEPLOY_DIR' -AppPort '$env:APP_PORT'; Start-Service -Name '$env:SERVICE_NAME'; Start-Sleep -Seconds 10; if ((Get-Service -Name '$env:SERVICE_NAME').Status -ne 'Running') { throw 'Service $env:SERVICE_NAME is not running' }`""
                    '''
                }
            }
        }

        stage('Health Check') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'coreano-windows-ssh-key',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    ),
                    file(credentialsId: 'coreano-windows-known-hosts', variable: 'KNOWN_HOSTS'),
                    string(credentialsId: 'coreano-windows-host', variable: 'WINDOWS_HOST')
                ]) {
                    powershell '''
                        $ErrorActionPreference = 'Stop'

                        $sshTarget = "$($env:SSH_USER)@$($env:WINDOWS_HOST)"
                        $sshOpts = @(
                            '-i', $env:SSH_KEY,
                            '-o', 'IdentitiesOnly=yes',
                            '-o', 'StrictHostKeyChecking=yes',
                            '-o', "UserKnownHostsFile=$env:KNOWN_HOSTS"
                        )

                        ssh @sshOpts $sshTarget "powershell -NoProfile -ExecutionPolicy Bypass -Command `"Invoke-RestMethod -Uri 'http://localhost:$env:APP_PORT/actuator/health' -TimeoutSec 20 | ConvertTo-Json -Compress`""
                    '''
                }
            }
        }
    }
}
