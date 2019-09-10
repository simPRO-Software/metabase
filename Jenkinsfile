pipeline {
  agent {
    docker {
      image 'metabasepackage'
      args '-v /.npm:/.npm -v /.cache:/.cache -v /.yarn:/.yarn'
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
    stage("run build") {
      steps {
        sh './bin/build no-translations'
      }
    }
  }
}
