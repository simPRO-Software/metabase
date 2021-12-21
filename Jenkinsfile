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
    stage("Clean workspace") {
      steps {
        // When the build runs, it creates a bunch of files as root.
        // These files can't be removed by jenkins outside of docker because jenkins doesn't have sudo.
        // So we do the cleanup inside docker
        sh 'git reset --hard'
        sh 'git clean -fdx'
      }
    }
    stage("Run build") {
      steps {
        sh './bin/build'
      }
    }
  }
}
