pipeline {
  agent {
    docker {
      image 'metabasepackage'
      args '-v /.npm:/.npm'
    }
  }
  stages {
    stage("print env variables") {
      steps {
        script {
          echo sh(script: 'env|sort', returnStdout: true)
        }
      }
    }
    stage("npm install") {
      steps {
        script {
          sh 'npm install'
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
