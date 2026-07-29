pipeline {
    agent { label 'windows' }
    options { disableConcurrentBuilds() }

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
                        $null = & icacls.exe $env:SSH_KEY /inheritance:r /grant:r "$($env:USERNAME):(R)"

                        $sshTarget = "$($env:SSH_USER)@$($env:WINDOWS_HOST)"
                        $sshExe = 'C:/Program Files/Git/usr/bin/ssh.exe'
                        $scpExe = 'C:/Program Files/Git/usr/bin/scp.exe'
                        $sshOpts = @(
                            '-i', $env:SSH_KEY,
                            '-p', $env:WINDOWS_SSH_PORT,
                            '-T',
                            '-o', 'IdentitiesOnly=yes',
                            '-o', 'BatchMode=yes',
                            '-o', 'ConnectTimeout=10',
                            '-o', 'HostKeyAlgorithms=ssh-ed25519',
                            '-o', 'StrictHostKeyChecking=yes',
                            '-o', "UserKnownHostsFile=$env:WORKSPACE/deploy/windows/known_hosts"
                        )
                        $scpOpts = @(
                            '-i', $env:SSH_KEY,
                            '-P', $env:WINDOWS_SSH_PORT,
                            '-o', 'IdentitiesOnly=yes',
                            '-o', 'BatchMode=yes',
                            '-o', 'ConnectTimeout=10',
                            '-o', 'HostKeyAlgorithms=ssh-ed25519',
                            '-o', 'StrictHostKeyChecking=yes',
                            '-o', "UserKnownHostsFile=$env:WORKSPACE/deploy/windows/known_hosts"
                        )
                        $jarName = [IO.Path]::GetFileName($env:APP_JAR)
                        $releaseName = "$($env:APP_NAME)-$($env:BUILD_NUMBER).jar"

                        $ErrorActionPreference = 'Continue'
                        & $scpExe @scpOpts $env:APP_JAR "deploy/windows/install-coreano-service.ps1" "$sshTarget`:./"
                        if ($LASTEXITCODE) { exit $LASTEXITCODE }
                        Start-Sleep -Seconds 2
                        & $sshExe -n @sshOpts $sshTarget "powershell -NoProfile -ExecutionPolicy Bypass -File install-coreano-service.ps1 -ServiceName $env:SERVICE_NAME -DeployDir $env:DEPLOY_DIR -AppPort $env:APP_PORT -ReleaseJar $jarName -ReleaseName $releaseName"
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
                        $null = & icacls.exe $env:SSH_KEY /inheritance:r /grant:r "$($env:USERNAME):(R)"

                        $sshTarget = "$($env:SSH_USER)@$($env:WINDOWS_HOST)"
                        $sshExe = 'C:/Program Files/Git/usr/bin/ssh.exe'
                        $sshOpts = @(
                            '-i', $env:SSH_KEY,
                            '-p', $env:WINDOWS_SSH_PORT,
                            '-T',
                            '-o', 'IdentitiesOnly=yes',
                            '-o', 'BatchMode=yes',
                            '-o', 'ConnectTimeout=10',
                            '-o', 'HostKeyAlgorithms=ssh-ed25519',
                            '-o', 'StrictHostKeyChecking=yes',
                            '-o', "UserKnownHostsFile=$env:WORKSPACE/deploy/windows/known_hosts"
                        )

                        $ErrorActionPreference = 'Continue'
                        & $sshExe -n @sshOpts $sshTarget "powershell -NoProfile -ExecutionPolicy Bypass -Command Invoke-RestMethod -Uri http://localhost:$env:APP_PORT/actuator/health -TimeoutSec 20"
                        if ($LASTEXITCODE) { exit $LASTEXITCODE }
                    '''
                }
            }
        }
    }
}
