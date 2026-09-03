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

        // Database
        DB_USERNAME = 'postgres'
        DB_PASSWORD = 'postgres'
        DB_NAME = 'expense_management'

        // Datasource
        SPRING_DATASOURCE_URL = 'jdbc:postgresql://postgres:5432/expense_management'
        SPRING_DATASOURCE_DRIVER = 'org.postgresql.Driver'
        SPRING_JPA_DDL_AUTO = 'none'
        SPRING_JPA_DIALECT = 'org.hibernate.dialect.PostgreSQLDialect'
        SPRING_JPA_FORMAT_SQL = 'false'

        // Redis
        REDIS_HOST = 'redis'
        REDIS_PORT = '6379'
        REDIS_TIMEOUT = '2000ms'

        // JWT — default value, not read from Jenkins credentials
        JWT_SECRET = 'change-me-in-production-00000000000000000000000000000000'

        // Application URLs — must include the scheme: the browser Origin header
        // and password-reset links are scheme-qualified (https://). Without it,
        // CORS and OriginCheckFilter reject every browser request.
        BASE_URL = 'https://expm-api.ouklymeng.qzz.io'
        FRONTEND_URL = 'https://expm.ouklymeng.qzz.io'

        // Rate Limiting
        RATE_LIMITING_PER_USER_FORGOT_PASSWORD = '10'
        RATE_LIMITING_PER_USER_RESET_PASSWORD = '10'
        RATE_LIMITING_PER_USER_MFA_VERIFY = '10'

        // Logging
        LOG_LEVEL_APP = 'INFO'
        LOG_LEVEL_SECURITY = 'WARN'

        // Mail — optional; required for email MFA and password-reset emails.
        // When enabled (APP_MAIL_ENABLED=true), set MAIL_HOST/MAIL_USERNAME and
        // MAIL_PASSWORD to real values.
        APP_MAIL_ENABLED = 'false'
        MAIL_HOST = ''
        MAIL_PORT = '587'
        MAIL_USERNAME = ''
        MAIL_PASSWORD = ''

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
                        credentialsId: 'dockerhub',
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
                sshagent(credentials: ['ssh-key']) {
                    sh '''
                        set -eu

                        echo "Deploying ${APP_NAME}..."
                        echo "Target: ${DEPLOY_USER}@${DEPLOY_HOST}"

                        scp \
                            -o StrictHostKeyChecking=no \
                            -o UserKnownHostsFile=/dev/null \
                            docker-compose.yml \
                            "${DEPLOY_USER}@${DEPLOY_HOST}:/opt/${APP_NAME}/docker-compose.yml"

                        scp \
                            -o StrictHostKeyChecking=no \
                            -o UserKnownHostsFile=/dev/null \
                            Caddyfile \
                            "${DEPLOY_USER}@${DEPLOY_HOST}:/opt/${APP_NAME}/Caddyfile"

                        ssh \
                            -o StrictHostKeyChecking=no \
                            -o UserKnownHostsFile=/dev/null \
                            "${DEPLOY_USER}@${DEPLOY_HOST}" \
                            "APP_NAME='${APP_NAME}' \
                             DOCKER_IMAGE='${DOCKER_IMAGE}' \
                             IMAGE_TAG='${IMAGE_TAG}' \
                             DB_USERNAME='${DB_USERNAME}' \
                             DB_PASSWORD='${DB_PASSWORD}' \
                             DB_NAME='${DB_NAME}' \
                             REDIS_HOST='${REDIS_HOST}' \
                             REDIS_PORT='${REDIS_PORT}' \
                             REDIS_TIMEOUT='${REDIS_TIMEOUT}' \
                             SPRING_DATASOURCE_URL='${SPRING_DATASOURCE_URL}' \
                             SPRING_DATASOURCE_DRIVER='${SPRING_DATASOURCE_DRIVER}' \
                             SPRING_JPA_DDL_AUTO='${SPRING_JPA_DDL_AUTO}' \
                             SPRING_JPA_DIALECT='${SPRING_JPA_DIALECT}' \
                             SPRING_JPA_FORMAT_SQL='${SPRING_JPA_FORMAT_SQL}' \
                             LOG_LEVEL_APP='${LOG_LEVEL_APP}' \
                             LOG_LEVEL_SECURITY='${LOG_LEVEL_SECURITY}' \
                             JWT_SECRET='${JWT_SECRET}' \
                             BASE_URL='${BASE_URL}' \
                             FRONTEND_URL='${FRONTEND_URL}' \
                             RATE_LIMITING_PER_USER_FORGOT_PASSWORD='${RATE_LIMITING_PER_USER_FORGOT_PASSWORD}' \
                             RATE_LIMITING_PER_USER_RESET_PASSWORD='${RATE_LIMITING_PER_USER_RESET_PASSWORD}' \
                             RATE_LIMITING_PER_USER_MFA_VERIFY='${RATE_LIMITING_PER_USER_MFA_VERIFY}' \
                             APP_MAIL_ENABLED='${APP_MAIL_ENABLED}' \
                             MAIL_HOST='${MAIL_HOST}' \
                             MAIL_PORT='${MAIL_PORT}' \
                             MAIL_USERNAME='${MAIL_USERNAME}' \
                             MAIL_PASSWORD='${MAIL_PASSWORD}' \
                             bash -s" <<'REMOTE_SCRIPT'

                        set -eu

                        cd "/opt/${APP_NAME}"

                        export APP_NAME="${APP_NAME}"
                        export DOCKER_IMAGE="${DOCKER_IMAGE}"
                        export IMAGE_TAG="${IMAGE_TAG}"
                        export DB_USERNAME="${DB_USERNAME}"
                        export DB_PASSWORD="${DB_PASSWORD}"
                        export DB_NAME="${DB_NAME}"
                        export REDIS_HOST="${REDIS_HOST}"
                        export REDIS_PORT="${REDIS_PORT}"
                        export REDIS_TIMEOUT="${REDIS_TIMEOUT}"
                        export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL}"
                        export SPRING_DATASOURCE_DRIVER="${SPRING_DATASOURCE_DRIVER}"
                        export SPRING_JPA_DDL_AUTO="${SPRING_JPA_DDL_AUTO}"
                        export SPRING_JPA_DIALECT="${SPRING_JPA_DIALECT}"
                        export SPRING_JPA_FORMAT_SQL="${SPRING_JPA_FORMAT_SQL}"
                        export LOG_LEVEL_APP="${LOG_LEVEL_APP}"
                        export LOG_LEVEL_SECURITY="${LOG_LEVEL_SECURITY}"
                        export JWT_SECRET="${JWT_SECRET}"
                        export BASE_URL="${BASE_URL}"
                        export FRONTEND_URL="${FRONTEND_URL}"
                        export RATE_LIMITING_PER_USER_FORGOT_PASSWORD="${RATE_LIMITING_PER_USER_FORGOT_PASSWORD}"
                        export RATE_LIMITING_PER_USER_RESET_PASSWORD="${RATE_LIMITING_PER_USER_RESET_PASSWORD}"
                        export RATE_LIMITING_PER_USER_MFA_VERIFY="${RATE_LIMITING_PER_USER_MFA_VERIFY}"
                        export APP_MAIL_ENABLED="${APP_MAIL_ENABLED}"
                        export MAIL_HOST="${MAIL_HOST}"
                        export MAIL_PORT="${MAIL_PORT}"
                        export MAIL_USERNAME="${MAIL_USERNAME}"
                        export MAIL_PASSWORD="${MAIL_PASSWORD}"

                        echo "Pulling image:"
                        echo "${DOCKER_IMAGE}:${IMAGE_TAG}"

                        docker pull "${DOCKER_IMAGE}:${IMAGE_TAG}"

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
                sshagent(credentials: ['ssh-key']) {
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
