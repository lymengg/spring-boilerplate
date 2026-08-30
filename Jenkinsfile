pipeline {
    agent {
        label 'docker'
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    environment {
        APP_NAME = 'expense-management-api'
        DOCKER_IMAGE = 'lymengouk/expense-management-api'
        BRANCH_NAME = 'main'
        // Jenkins credentials
        DOCKER_CREDENTIALS_ID = 'dockerhub'
        SSH_CREDENTIALS_ID    = 'ssh-key'

        // Deployment target
        DEPLOY_HOST = '172.31.26.3'
        DEPLOY_USER = 'deploy'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm

                script {
                    env.GIT_COMMIT_SHORT = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()

                    env.IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"

                    echo "Application : ${env.APP_NAME}"
                    echo "Branch      : ${env.BRANCH_NAME}"
                    echo "Commit      : ${env.GIT_COMMIT_SHORT}"
                    echo "Image       : ${env.DOCKER_IMAGE}:${env.IMAGE_TAG}"
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                    set -eu

                    echo "Building Docker image..."

                    docker build \
                        -t "${DOCKER_IMAGE}:${IMAGE_TAG}" \
                        -t "${DOCKER_IMAGE}:latest" \
                        .
                '''
            }
        }

        stage('Push Docker Image') {
            when {
                branch 'main'
            }

            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: env.DOCKER_CREDENTIALS_ID,
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        set -eu

                        echo "${DOCKER_PASSWORD}" | docker login \
                            -u "${DOCKER_USERNAME}" \
                            --password-stdin

                        docker push "${DOCKER_IMAGE}:${IMAGE_TAG}"
                        docker push "${DOCKER_IMAGE}:latest"

                        docker logout
                    '''
                }
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }

            steps {
                sshagent(credentials: [env.SSH_CREDENTIALS_ID]) {
                    sh '''
                        set -eu

                        echo "Deploying ${APP_NAME}..."
                        echo "Target: ${DEPLOY_USER}@${DEPLOY_HOST}"

                        ssh \
                            -o StrictHostKeyChecking=no \
                            -o UserKnownHostsFile=/dev/null \
                            "${DEPLOY_USER}@${DEPLOY_HOST}" \
                            "APP_NAME='${APP_NAME}' \
                             DOCKER_IMAGE='${DOCKER_IMAGE}' \
                             IMAGE_TAG='${IMAGE_TAG}' \
                             bash -s" <<'REMOTE_SCRIPT'

                        set -eu

                        cd "/opt/${APP_NAME}"

                        export APP_NAME="${APP_NAME}"
                        export DOCKER_IMAGE="${DOCKER_IMAGE}"
                        export IMAGE_TAG="${IMAGE_TAG}"

                        echo "Pulling image:"
                        echo "${DOCKER_IMAGE}:${IMAGE_TAG}"

                        docker pull "${DOCKER_IMAGE}:${IMAGE_TAG}"

                        echo "Updating IMAGE_TAG..."

                        sed -i \
                            "s|^IMAGE_TAG=.*|IMAGE_TAG=${IMAGE_TAG}|" \
                            .env

                        echo "Pulling Docker Compose image..."

                        docker compose pull

                        echo "Starting application..."

                        docker compose up -d --remove-orphans

                        echo "Waiting for application..."

                        sleep 10

                        echo "Container status:"

                        docker compose ps

                        echo "Deployment completed."

                        docker image prune -f

REMOTE_SCRIPT
                    '''
                }
            }
        }

        stage('Verify Deployment') {
            when {
                branch 'main'
            }

            steps {
                sshagent(credentials: [env.SSH_CREDENTIALS_ID]) {
                    sh '''
                        set -eu

                        echo "Verifying deployment..."

                        ssh \
                            -o StrictHostKeyChecking=no \
                            -o UserKnownHostsFile=/dev/null \
                            "${DEPLOY_USER}@${DEPLOY_HOST}" \
                            "cd /opt/${APP_NAME} && docker compose ps"
                    '''
                }
            }
        }
    }

    post {
        cleanup {
            script {
                try {
                    cleanWs(
                        deleteDirs: true,
                        disableDeferredWipeout: true
                    )
                } catch (Exception e) {
                    echo "Workspace cleanup skipped: ${e.message}"
                }
            }
        }
    }
}