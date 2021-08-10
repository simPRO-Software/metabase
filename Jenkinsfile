pipeline {
  agent {
    docker {
      image 'metabasepackage'
      args '-v /.npm:/.npm -v /.cache:/.cache -v /.yarn:/.yarn'
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
        sh './bin/build no-translations'
      }
    }
  }
  post {
    success {
      archiveArtifacts artifacts: 'target/uberjar/metabase.jar'
      slackSend color: 'good', channel: '#jenkins-metabasepackage', message: "*SUCCESSED* - Packaged metabase.  Download jar file from Jenkins: <${env.BUILD_URL}|build ${env.BUILD_NUMBER}>"
    }
    failure {
      slackSend color: 'danger', channel: '#jenkins-metabasepackage', message: "*FAILED* - Metabase build failed (<${env.BUILD_URL}|build ${env.BUILD_NUMBER}>) - <${env.BUILD_URL}console|click here to see the console output>"
    }
  }
}
