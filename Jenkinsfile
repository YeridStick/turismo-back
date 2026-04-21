pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    parameters {
        booleanParam(name: 'RUN_TESTS', defaultValue: false, description: 'Ejecutar pruebas unitarias')
        string(name: 'GIT_BRANCH', defaultValue: 'master', description: 'Rama a construir')
    }

    environment {
        GRADLE_ARGS = '--no-daemon --stacktrace'
        // Variables de configuración de AWS (No secretas)
        AWS_REGION    = 'us-east-1'
        ECR_REPO_NAME = 'turismo/backend'
        AWS_ACCESS_KEY_ID     = credentials('AWS_ACCESS_KEY_ID')
        AWS_SECRET_ACCESS_KEY = credentials('AWS_SECRET_ACCESS_KEY')
        AWS_ACCOUNT_ID        = credentials('AWS_ACCOUNT_ID')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${params.GIT_BRANCH}"]],
                    userRemoteConfigs: [[url: 'https://github.com/YeridStick/turismo-back.git']]
                ])
            }
        }

        stage('Build Jar') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'chmod +x gradlew'
                        sh "./gradlew ${env.GRADLE_ARGS} :app-service:bootJar -x test -x validateStructure"
                    } else {
                        bat ".\\gradlew.bat ${env.GRADLE_ARGS} :app-service:bootJar -x test -x validateStructure"
                    }
                }
            }
        }

        stage('Tests') {
            when {
                expression { return params.RUN_TESTS }
            }
            steps {
                script {
                    if (isUnix()) {
                        sh "./gradlew ${env.GRADLE_ARGS} test -x validateStructure"
                    } else {
                        bat ".\\gradlew.bat ${env.GRADLE_ARGS} test -x validateStructure"
                    }
                }
            }
        }

        stage('Archive Artifact') {
            steps {
                archiveArtifacts artifacts: 'applications/app-service/build/libs/*.jar', fingerprint: true
            }
        }

        stage('Diagnostico') {
            steps {
                sh 'docker --version'
                sh 'aws --version'
                sh 'aws sts get-caller-identity' // Confirma que AWS Auth funciona
            }
        }

        // --- NUEVO STAGE PARA DOCKER Y ECR ---
        stage('Docker Build & Push') {
            steps {
                withCredentials([
                    string(credentialsId: 'AWS_ACCOUNT_ID', variable: 'ACCOUNT_ID'),
                    // Solo si Jenkins NO corre en EC2 con IAM Role:
                    // string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'AWS_ACCESS_KEY_ID'),
                    // string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    script {
                        def ecrUrl = "${ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"
                        def fullImage = "${ecrUrl}/${env.ECR_REPO_NAME}:latest"

                        echo "Autenticando en ECR..."
                        sh "aws ecr get-login-password --region ${env.AWS_REGION} | docker login --username AWS --password-stdin ${ecrUrl}"

                        echo "Construyendo imagen Docker..."
                        sh "docker build -t ${fullImage} -f deployment/Dockerfile ."

                        echo "Subiendo imagen a ECR..."
                        sh "docker push ${fullImage}"
                    }
                }
            }
        }
    }

    post {
        always {
            deleteDir()
        }
        success {
            echo 'Pipeline finalizado y desplegado en ECR correctamente.'
        }
        failure {
            echo 'Pipeline falló. Revisa el stage y logs.'
        }
    }
}