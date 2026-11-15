pipeline {
  agent {
    docker {
      // Same builder image the repo's Dockerfile uses (Clojure CLI 1.11 / node 16). The previous
      // 2021 image ships Clojure CLI 1.10.3.822, older than the 1.10.3.905 that bin/build requires.
      image 'metabase/ci:java-11-clj-1.11.0.1100.04-2022-build'
      args '-v /.npm:/.npm -v /.cache:/.cache -v /.yarn:/.yarn -u root:root'
    }
  }
  environment {
    HOME = "/.cache"
    // Production runs without an enterprise token, so build the OSS edition explicitly.
    MB_EDITION = "oss"
    INTERACTIVE = "false"
    CI = "true"
  }
  stages {
    stage("print env variables") {
      steps {
        script {
          echo sh(script: 'env|sort', returnStdout: true)
        }
      }
    }
    stage("clean workspace") {
      steps {
        // bin/build only deletes target/uberjar/metabase.jar. Anything else left in the workspace from
        // an earlier build is packaged into the new jar: old hashed frontend bundles + source maps in
        // resources/frontend_client/app/dist and driver jars in resources/modules that no longer exist
        // in modules/drivers (druid-jdbc, bigquery, google). That added ~28 MB of dead files to the
        // production jar. Remove every untracked/ignored file except the dependency caches.
        sh '''
          git clean -fdx \
            -e node_modules \
            -e .cache \
            -e .npm \
            -e .yarn
          rm -rf resources/frontend_client/app/dist resources/modules target
        '''
      }
    }
    stage("run build") {
      steps {
        sh './bin/build'
      }
    }
    stage("verify jar") {
      steps {
        // Fail fast if stale artifacts still made it in: only the un-hashed *.bundle.js files that
        // index.html references are expected, and only drivers built from modules/drivers.
        // (the CI image has no `unzip`, so use the JDK `jar` tool)
        sh '''
          JAR=target/uberjar/metabase.jar
          jar tf "$JAR" > /tmp/jar-contents.txt
          jar xf "$JAR" version.properties && cat version.properties
          echo "--- frontend bundles ---"
          grep -E '^frontend_client/app/dist/.*\\.js$' /tmp/jar-contents.txt
          if grep -E '^frontend_client/app/dist/.*\\.[0-9a-f]{20}\\.js$' /tmp/jar-contents.txt; then
            echo "ERROR: stale hashed frontend bundles found in jar"; exit 1
          fi
          echo "--- driver modules ---"
          grep -E '^modules/.*\\.jar$' /tmp/jar-contents.txt
          if grep -E '^modules/(druid-jdbc|bigquery|google)\\.metabase-driver\\.jar$' /tmp/jar-contents.txt; then
            echo "ERROR: stale driver jar found in jar"; exit 1
          fi
        '''
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
