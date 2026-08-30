pipeline {
    agent {
        label 'linux'
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    environment {
        APP_NAME           = 'spring-boilerplate'
        DOCKER_REGISTRY    = 'docker.io'
        DOCKER_IMAGE       = 'lymengouk/spring-boilerplate'

        DOCKER_CREDENTIALS = 'dockerhub'
        SSH_CREDENTIALS    = 'ssh-key'

        STAGING_HOST       = credentials('staging-host')
        PRODUCTION_HOST    = credentials('production-host')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prepare') {
            steps {
                script {
                    def gitCommit = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()

                    env.GIT_COMMIT_SHORT = gitCommit
                    env.IMAGE_TAG = "${BUILD_NUMBER}-${gitCommit}"

                    echo "Branch: ${BRANCH_NAME}"
                    echo "Commit: ${GIT_COMMIT_SHORT}"
                    echo "Image: ${DOCKER_IMAGE}:${IMAGE_TAG}"
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    set -e

                    docker build \
                        --pull \
                        -t "$DOCKER_IMAGE:$IMAGE_TAG" \
                        .

                    docker tag \
                        "$DOCKER_IMAGE:$IMAGE_TAG" \
                        "$DOCKER_IMAGE:latest"
                '''
            }
        }

        stage('Docker Push') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'main'
                }
            }

            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: "${DOCKER_CREDENTIALS}",
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        set -e

                        echo "$DOCKER_PASSWORD" | docker login \
                            "$DOCKER_REGISTRY" \
                            --username "$DOCKER_USERNAME" \
                            --password-stdin

                        docker push "$DOCKER_IMAGE:$IMAGE_TAG"
                        docker push "$DOCKER_IMAGE:latest"

                        docker logout "$DOCKER_REGISTRY"
                    '''
                }
            }
        }

        stage('Production Approval') {
            when {
                branch 'main'
            }

            input {
                message 'Deploy this image to production?'
                ok 'Deploy to Production'
                submitterParameter 'APPROVED_BY'
            }

            steps {
                script {
                    echo "Production deployment approved by: ${env.APPROVED_BY ?: 'unknown'}"
                }
            }
        }

        stage('Deploy') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'main'
                }
            }

            steps {
                script {
                    def deployHost = env.BRANCH_NAME == 'main'
                        ? env.PRODUCTION_HOST
                        : env.STAGING_HOST

                    echo "Deploying ${IMAGE_TAG} to ${env.BRANCH_NAME == 'main' ? 'production' : 'staging'}"

                    withEnv(["DEPLOY_HOST=${deployHost}"]) {
                        sshagent(credentials: ["${SSH_CREDENTIALS}"]) {
                            sh '''
                                set -e

                                ssh \
                                    -o BatchMode=yes \
                                    -o StrictHostKeyChecking=no \
                                    -o UserKnownHostsFile=/dev/null \
                                    deploy@"$DEPLOY_HOST" \
                                    "export APP_NAME='$APP_NAME' IMAGE='$DOCKER_IMAGE:$IMAGE_TAG' && \
                                     cd /opt/\\$APP_NAME && \
                                     docker pull \\$IMAGE && \
                                     docker compose -f docker-compose.yml up -d"
                            '''
                        }
                    }
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
                if (env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'develop') {
                    slackSend(
                        color: 'good',
                        message: """
:white_check_mark: *${APP_NAME}* build #${BUILD_NUMBER} succeeded

Branch: ${BRANCH_NAME}
Commit: ${GIT_COMMIT_SHORT}
Image: ${DOCKER_IMAGE}:${IMAGE_TAG}
Build: ${BUILD_URL}
""".stripIndent()
                    )
                }
            }
        }

        failure {
            script {
                slackSend(
                    color: 'danger',
                    message: """
:x: *${APP_NAME}* build #${BUILD_NUMBER} failed

Branch: ${BRANCH_NAME}
Commit: ${GIT_COMMIT_SHORT ?: 'unknown'}
Build: ${BUILD_URL}
""".stripIndent()
                )
            }
        }

        unstable {
            script {
                slackSend(
                    color: 'warning',
                    message: """
:warning: *${APP_NAME}* build #${BUILD_NUMBER} is unstable

Branch: ${BRANCH_NAME}
Commit: ${GIT_COMMIT_SHORT ?: 'unknown'}
Build: ${BUILD_URL}
""".stripIndent()
                )
            }
        }
    }
}