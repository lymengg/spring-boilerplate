pipeline {
    agent {
        docker {
            image 'eclipse-temurin:21-jdk-alpine'
            args '-v $HOME/.m2:/root/.m2'
        }
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    environment {
        APP_NAME            = 'spring-boilerplate'
        DOCKER_REGISTRY     = env.DOCKER_REGISTRY ?: 'docker.io'
        DOCKER_IMAGE        = "${DOCKER_REGISTRY}/${APP_NAME}"
        DOCKER_CREDENTIALS  = credentials('docker-credentials')
        SONAR_TOKEN         = credentials('sonar-token')
        JAVA_OPTS           = '-Xmx1024m -Xms512m'
        MAVEN_OPTS          = '-Xmx1024m -Xms512m'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh './mvnw clean compile -B'
            }
        }

        stage('Test') {
            parallel {
                stage('Unit Tests') {
                    steps {
                        sh './mvnw test -B'
                    }
                    post {
                        always {
                            junit '**/target/surefire-reports/*.xml'
                        }
                    }
                }

                stage('Code Quality') {
                    steps {
                        sh './mvnw verify -B -DskipTests'
                    }
                }
            }
        }

        stage('Package') {
            steps {
                sh './mvnw package -B -DskipTests'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    def gitCommit = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    def buildTag = "${env.BUILD_NUMBER}-${gitCommit}"
                    env.IMAGE_TAG = buildTag

                    sh """
                        docker build -t ${DOCKER_IMAGE}:${buildTag} .
                        docker tag ${DOCKER_IMAGE}:${buildTag} ${DOCKER_IMAGE}:latest
                    """
                }
            }
        }

        stage('Docker Push') {
            when {
                branch 'main'
            }
            steps {
                sh """
                    echo ${DOCKER_PASSWORD_PSW} | docker login ${DOCKER_REGISTRY} -u ${DOCKER_USERNAME_PSW} --password-stdin
                    docker push ${DOCKER_IMAGE}:${IMAGE_TAG}
                    docker push ${DOCKER_IMAGE}:latest
                """
            }
        }

        stage('Deploy to Staging') {
            when {
                branch 'main'
            }
            steps {
                sshagent(['staging-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no deploy@${STAGING_HOST} \
                            "docker pull ${DOCKER_IMAGE}:${IMAGE_TAG} && \
                             docker compose -f /opt/${APP_NAME}/docker-compose.yml up -d"
                    """
                }
            }
        }

        stage('Deploy to Production') {
            when {
                branch 'main'
            }
            input {
                message 'Deploy to production?'
                ok 'Deploy'
            }
            steps {
                sshagent(['production-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no deploy@${PROD_HOST} \
                            "docker pull ${DOCKER_IMAGE}:${IMAGE_TAG} && \
                             docker compose -f /opt/${APP_NAME}/docker-compose.yml up -d"
                    """
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            script {
                if (env.BRANCH_NAME == 'main') {
                    slackSend(
                        color: 'good',
                        message: ":white_check_mark: *${APP_NAME}* build #${env.BUILD_NUMBER} succeeded\nBranch: ${env.BRANCH_NAME}\nCommit: ${env.GIT_COMMIT?.take(7)}"
                    )
                }
            }
        }
        failure {
            script {
                slackSend(
                    color: 'danger',
                    message: ":x: *${APP_NAME}* build #${env.BUILD_NUMBER} failed\nBranch: ${env.BRANCH_NAME}\nURL: ${env.BUILD_URL}"
                )
            }
        }
        unstable {
            script {
                slackSend(
                    color: 'warning',
                    message: ":warning: *${APP_NAME}* build #${env.BUILD_NUMBER} is unstable\nBranch: ${env.BRANCH_NAME}\nURL: ${env.BUILD_URL}"
                )
            }
        }
    }
}
