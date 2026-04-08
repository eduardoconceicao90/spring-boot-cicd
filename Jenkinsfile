pipeline {
    agent any

    tools {
        maven 'Maven-3.9'
        jdk 'JDK-21'
    }

    environment {
        DOCKER_IMAGE = 'spring-boot-cicd'
        DOCKER_TAG   = "${env.BUILD_NUMBER}"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh 'mvn clean package'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                    jacoco execPattern: 'target/jacoco.exec'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} ."
                sh "docker tag ${DOCKER_IMAGE}:${DOCKER_TAG} ${DOCKER_IMAGE}:latest"
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    docker network create monitoring 2>/dev/null || true
                    docker stop spring-boot-app 2>/dev/null || true
                    docker rm spring-boot-app 2>/dev/null || true
                    docker run -d \
                        --name spring-boot-app \
                        --network monitoring \
                        -p 8080:8080 \
                        spring-boot-cicd:latest
                '''
            }
        }
    }

    post {
        success {
            echo 'Pipeline concluído com sucesso!'
        }
        failure {
            echo 'Pipeline falhou!'
        }
        always {
            cleanWs()
        }
    }
}
