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
        DB_PASSWORD = credentials('DB_PASSWORD')
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

        // JWT — signing key, injected from the Jenkins credential store
        JWT_SECRET = credentials('JWT_SECRET')

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

        // Server — the app runs behind the Caddy reverse proxy, so scheme and
        // client IP come from the X-Forwarded-* headers Caddy sets.
        SERVER_FORWARD_HEADERS_STRATEGY = 'framework'

        // Mail — disabled; mail autoconfiguration is excluded in all profiles.
        APP_MAIL_ENABLED = 'false'

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

                        # Deployment config is delivered as an .env file next to
                        # docker-compose.yml: compose reads it automatically, and the
                        # values never appear in the remote process list.
                        umask 077

                        cat > deploy.env <<EOF
DOCKER_IMAGE=${DOCKER_IMAGE}
IMAGE_TAG=${IMAGE_TAG}
DB_USERNAME=${DB_USERNAME}
DB_PASSWORD=${DB_PASSWORD}
DB_NAME=${DB_NAME}
REDIS_HOST=${REDIS_HOST}
REDIS_PORT=${REDIS_PORT}
REDIS_TIMEOUT=${REDIS_TIMEOUT}
SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL}
SPRING_DATASOURCE_DRIVER=${SPRING_DATASOURCE_DRIVER}
SPRING_JPA_DDL_AUTO=${SPRING_JPA_DDL_AUTO}
SPRING_JPA_DIALECT=${SPRING_JPA_DIALECT}
SPRING_JPA_FORMAT_SQL=${SPRING_JPA_FORMAT_SQL}
LOG_LEVEL_APP=${LOG_LEVEL_APP}
LOG_LEVEL_SECURITY=${LOG_LEVEL_SECURITY}
SERVER_FORWARD_HEADERS_STRATEGY=${SERVER_FORWARD_HEADERS_STRATEGY}
JWT_SECRET=${JWT_SECRET}
BASE_URL=${BASE_URL}
FRONTEND_URL=${FRONTEND_URL}
RATE_LIMITING_PER_USER_FORGOT_PASSWORD=${RATE_LIMITING_PER_USER_FORGOT_PASSWORD}
RATE_LIMITING_PER_USER_RESET_PASSWORD=${RATE_LIMITING_PER_USER_RESET_PASSWORD}
RATE_LIMITING_PER_USER_MFA_VERIFY=${RATE_LIMITING_PER_USER_MFA_VERIFY}
APP_MAIL_ENABLED=${APP_MAIL_ENABLED}
EOF

                        scp \
                            -o StrictHostKeyChecking=no \
                            -o UserKnownHostsFile=/dev/null \
                            deploy.env \
                            "${DEPLOY_USER}@${DEPLOY_HOST}:/opt/${APP_NAME}/.env"

                        rm -f deploy.env

                        ssh \
                            -o StrictHostKeyChecking=no \
                            -o UserKnownHostsFile=/dev/null \
                            "${DEPLOY_USER}@${DEPLOY_HOST}" \
                            "APP_NAME='${APP_NAME}' bash -s" <<'REMOTE_SCRIPT'

                        set -eu

                        cd "/opt/${APP_NAME}"

                        chmod 600 .env

                        echo "Pulling images..."

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
