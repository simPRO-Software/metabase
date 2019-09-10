pipeline {
  agent {
    docker {
      image 'metabasepackage'
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
        sh './bin/build'
      }
    }
  }
}
