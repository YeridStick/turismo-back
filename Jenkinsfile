pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(name: 'RUN_TESTS', defaultValue: false, description: 'Ejecutar pruebas unitarias')
        string(name: 'GIT_BRANCH', defaultValue: 'main', description: 'Rama a construir')
    }

    environment {
        GRADLE_ARGS = '--no-daemon --stacktrace'
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
    }

    post {
        always {
            deleteDir()
        }
        success {
            echo 'Pipeline finalizado correctamente.'
        }
        failure {
            echo 'Pipeline fallo. Revisa el stage y logs.'
        }
    }
}

