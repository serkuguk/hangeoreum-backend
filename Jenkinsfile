pipeline {
    agent any

    options {
        timestamps()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Jar') {
            steps {
                sh 'chmod +x mvnw'
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Deploy') {
            steps {
                sh 'docker compose up -d --build api'
            }
        }

        stage('Health Check') {
            steps {
                sh 'curl -fsS http://host.docker.internal:8082/actuator/health'
            }
        }
    }
}
