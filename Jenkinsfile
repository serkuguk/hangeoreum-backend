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
                bat 'chmod +x mvnw'
                bat './mvnw clean test package'
            }
        }

        stage('Find Artifact') {
            steps {
                script {
                    env.APP_JAR = bat(
                        script: "find target -maxdepth 1 -name '*.jar' ! -name '*.original' | head -n 1",
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
                    bat '''
                        set -eu

                        SSH_OPTS="-i $SSH_KEY -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=$KNOWN_HOSTS"
                        RELEASE_JAR="$DEPLOY_DIR/releases/$APP_NAME-$BUILD_NUMBER.jar"

                        ssh $SSH_OPTS "$SSH_USER@$WINDOWS_HOST" "powershell -NoProfile -ExecutionPolicy Bypass -Command \"New-Item -ItemType Directory -Force -Path '$DEPLOY_DIR','$DEPLOY_DIR/releases','$DEPLOY_DIR/uploads','$DEPLOY_DIR/scripts' | Out-Null\""
                        scp $SSH_OPTS "$APP_JAR" "$SSH_USER@$WINDOWS_HOST:$RELEASE_JAR"
                        scp $SSH_OPTS "deploy/windows/install-coreano-service.ps1" "$SSH_USER@$WINDOWS_HOST:$DEPLOY_DIR/scripts/install-coreano-service.ps1"
                        ssh $SSH_OPTS "$SSH_USER@$WINDOWS_HOST" "powershell -NoProfile -ExecutionPolicy Bypass -Command \"\$ErrorActionPreference = 'Stop'; Stop-Service -Name '$SERVICE_NAME' -ErrorAction SilentlyContinue; Copy-Item -Force '$RELEASE_JAR' '$DEPLOY_DIR/app.jar'; & '$DEPLOY_DIR/scripts/install-coreano-service.ps1' -ServiceName '$SERVICE_NAME' -DeployDir '$DEPLOY_DIR' -AppPort '$APP_PORT'; Start-Service -Name '$SERVICE_NAME'; Start-Sleep -Seconds 10; if ((Get-Service -Name '$SERVICE_NAME').Status -ne 'Running') { throw 'Service $SERVICE_NAME is not running' }\""
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
                    bat '''
                        set -eu

                        SSH_OPTS="-i $SSH_KEY -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=$KNOWN_HOSTS"
                        ssh $SSH_OPTS "$SSH_USER@$WINDOWS_HOST" "powershell -NoProfile -ExecutionPolicy Bypass -Command \"Invoke-RestMethod -Uri 'http://localhost:$APP_PORT/actuator/health' -TimeoutSec 20 | ConvertTo-Json -Compress\""
                    '''
                }
            }
        }
    }
}
