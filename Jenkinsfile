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
        GRADLE_ARGS   = '--no-daemon --stacktrace'
        CONTAINER_NAME  = 'api-turismo'

        AWS_REGION = credentials('AWS_REGION')
        EC2_INSTANCE_ID = credentials('EC2_INSTANCE_ID')
        ECR_REPO_NAME = credentials('ECR_REPO_NAME')
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
                sh 'chmod +x gradlew'
                sh "./gradlew ${env.GRADLE_ARGS} :app-service:bootJar -x test -x validateStructure"
            }
        }

        stage('Tests') {
            when {
                expression { return params.RUN_TESTS }
            }
            steps {
                sh "./gradlew ${env.GRADLE_ARGS} test -x validateStructure"
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
                sh 'aws sts get-caller-identity'
            }
        }

        stage('Docker Build & Push') {
            steps {
                script {
                    def ecrUrl = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"
                    def fullImage = "${ecrUrl}/${env.ECR_REPO_NAME}:latest"

                    sh "aws ecr get-login-password --region ${env.AWS_REGION} | docker login --username AWS --password-stdin ${ecrUrl}"
                    sh "docker build -t ${fullImage} -f deployment/Dockerfile ."
                    sh "docker push ${fullImage}"
                }
            }
        }

        stage('Start EC2') {
            steps {
                sh """
                    aws ec2 start-instances \
                      --instance-ids ${EC2_INSTANCE_ID} \
                      --region ${AWS_REGION} || true
                """
            }
        }

        stage('Wait EC2 Ready') {
            steps {
                sh """
                    aws ec2 wait instance-running \
                      --instance-ids ${EC2_INSTANCE_ID} \
                      --region ${AWS_REGION}

                    aws ec2 wait instance-status-ok \
                      --instance-ids ${EC2_INSTANCE_ID} \
                      --region ${AWS_REGION}
                """
            }
        }

        stage('Deploy on EC2 via SSM') {
            steps {
                script {
                    def ecrUrl = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"
                    def fullImage = "${ecrUrl}/${env.ECR_REPO_NAME}:latest"

                    sh """
                        aws ssm send-command \
                          --region ${AWS_REGION} \
                          --instance-ids ${EC2_INSTANCE_ID} \
                          --document-name "AWS-RunShellScript" \
                          --comment "Deploy turismo backend desde Jenkins" \
                          --parameters 'commands=[
                            "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ecrUrl}",
                            "docker pull ${fullImage}",
                            "docker rm -f ${CONTAINER_NAME} || true",
                            "docker run -d --name ${CONTAINER_NAME} --restart unless-stopped -p 7860:7860 ${fullImage}"
                          ]'
                    """
                }
            }
        }
    }

    post {
        always {
            deleteDir()
        }
        success {
            echo 'Pipeline finalizado: imagen en ECR y despliegue ejecutado en EC2.'
        }
        failure {
            echo 'Pipeline falló. Revisa logs.'
        }
    }
}