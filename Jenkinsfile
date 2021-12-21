pipeline {
  agent any
  stages {

    stage('Clean workspace') {
      steps {
        script {
          sh(script: 'sudo git reset --hard')
          sh(script: 'sudo git clean -fdx')
        }
      }
    }

    stage('Build metabase') {
      agent {
        docker {
          image 'metabase/ci:java-11-lein-2.9.6-clj-1.10.3.822-04-22-2021'
          args '-v /.npm:/.npm -v /.cache:/.cache -v /.yarn:/.yarn -u root:root -e MB_EDITION=ee'
        }
      }
      environment {
        HOME = "/.cache"
      }
      stages {
        stage("print env variables") {
          steps {
            script {
              echo sh(script: 'env|sort', returnStdout: true)
            }
          }
        }
        stage("run build") {
          steps {
            sh './bin/build'
          }
        }
      }
    }

  }
}
