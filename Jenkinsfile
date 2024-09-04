pipeline {
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
