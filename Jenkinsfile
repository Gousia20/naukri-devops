pipeline {
    agent any

    stages {

        stage('Azure Login Test') {
            steps {
                withCredentials([
                    azureServicePrincipal(
                        credentialsId: 'azure-sp-cred',
                        subscriptionIdVariable: 'Subscription_ID',
                        clientIdVariable: 'CLIENT_ID',
                        clientSecretVariable: 'CLIENT_SECRET',
                        tenantIdVariable: 'TENANT_ID'
                    )
                ]) {
                    bat '''
                        az login --service-principal ^
                          -u %CLIENT_ID% ^
                          -p %CLIENT_SECRET% ^
                          --tenant %TENANT_ID%

                        az account set --subscription %SUBSCRIPTION_ID%

                        az account show
                    '''
                }
            }
        }

        stage('Blob Test') {
            steps {
                bat '''
                    az storage container list ^
                      --account-name naukrisa ^
                      --auth-mode login ^
                      -o table
                '''
            }
        }

        stage('Check Tools') {
            steps {
                bat '''
                    java -version
                    mvn -version
                    node -v
                    npm -v
                '''
            }
        }

        stage('Build Backend') {
            steps {
                bat 'mvn -f backend\\pom.xml clean verify'
            }
        }

        stage('Build Frontend') {
            steps {
                bat '''
                    npm --prefix frontend install
                    npm --prefix frontend run build
                '''
            }
        }

        stage('Frontend Test') {
            steps {
                bat 'npm --prefix frontend run test:ci'
            }
        }

        stage('E2E Test') {
            steps {
                bat '''
                    npm --prefix e2e install
                    npx --prefix e2e playwright install chromium
                    npm --prefix e2e test
                '''
            }
        }

        stage('Package') {
            steps {
                bat 'npm install'
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: '**\\*.exe, **\\*.zip',
                                 allowEmptyArchive: true
            }
        }

        stage('Upload to Azure') {
            steps {
                bat '''
                    az storage blob upload-batch ^
                      --account-name naukrisa ^
                      --destination naukri ^
                      --source build ^
                      --auth-mode login ^
                      --overwrite
                '''
            }
        }
    }
}