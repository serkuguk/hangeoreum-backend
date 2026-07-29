pipeline {
    agent { label 'windows' }
    options {
        disableConcurrentBuilds()
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

                    stash(
                        name: 'windows-deploy',
                        includes: 'target/*.jar,deploy/windows/install-coreano-service.ps1,deploy/windows/known_hosts'
                    )
                }
            }
        }

        stage('Deploy to Windows Server') {
            agent { label 'built-in' }
            steps {
                unstash 'windows-deploy'
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'node-windows-ssh',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {
                    sh '''
                        set -eu
                        chmod 600 "$SSH_KEY"
                        jar="$(find target -maxdepth 1 -type f -name '*.jar' | head -n 1)"
                        test -n "$jar"
                        jar_name="$(basename "$jar")"
                        release_name="$APP_NAME-$BUILD_NUMBER.jar"
                        ssh_target="$SSH_USER@$WINDOWS_HOST"
                        known_hosts="$WORKSPACE/deploy/windows/known_hosts"

                        scp -i "$SSH_KEY" -P "$WINDOWS_SSH_PORT" \
                            -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=10 \
                            -o HostKeyAlgorithms=ssh-ed25519 -o StrictHostKeyChecking=yes \
                            -o "UserKnownHostsFile=$known_hosts" \
                            "$jar" deploy/windows/install-coreano-service.ps1 "${ssh_target}:./"

                        ssh -n -T -i "$SSH_KEY" -p "$WINDOWS_SSH_PORT" \
                            -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=10 \
                            -o HostKeyAlgorithms=ssh-ed25519 -o StrictHostKeyChecking=yes \
                            -o "UserKnownHostsFile=$known_hosts" \
                            "$ssh_target" \
                            "powershell -NoProfile -ExecutionPolicy Bypass -File install-coreano-service.ps1 -ServiceName $SERVICE_NAME -DeployDir $DEPLOY_DIR -AppPort $APP_PORT -ReleaseJar $jar_name -ReleaseName $release_name"
                    '''
                }
            }
        }

        stage('Health Check') {
            agent { label 'built-in' }
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'node-windows-ssh',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {
                    sh '''
                        set -eu
                        chmod 600 "$SSH_KEY"
                        ssh -n -T -i "$SSH_KEY" -p "$WINDOWS_SSH_PORT" \
                            -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=10 \
                            -o HostKeyAlgorithms=ssh-ed25519 -o StrictHostKeyChecking=yes \
                            -o "UserKnownHostsFile=$WORKSPACE/deploy/windows/known_hosts" \
                            "$SSH_USER@$WINDOWS_HOST" \
                            "powershell -NoProfile -ExecutionPolicy Bypass -Command Invoke-RestMethod -Uri http://localhost:$APP_PORT/actuator/health -TimeoutSec 20"
                    '''
                }
            }
        }
    }
}
