#!/usr/bin/env bash

set -euo pipefail

MVN_REPOSITORY="new_nexus::default::https://repo.aurin.cloud.edu.au/repository/maven-aurin-geotools"
# MVN_EVALUATE_PLUGIN="org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate"
BUILDKITE_BUILD_NUMBER="${BUILDKITE_BUILD_NUMBER:-1}"
export JAVA_HOME="${JAVA_7_HOME}"

export PATH="${JAVA_7_HOME}/bin:${PATH}"

set -x

function bootstrap(){
  echo "--- Bootstrapping Javadoc"
  local base_dir="${PWD}"
  pushd build/maven/javadoc
  mvn -B -Djava.awt.headless=true --toolchains="${base_dir}/toolchains.xml" -Paurin-tools install
  popd
}

function clean(){
  echo "--- Cleaning"
  mvn -B -Djava.awt.headless=true --toolchains=toolchains.xml -Paurin-tools clean
}

function mvn_test {
  clean
  bootstrap
  echo "--- Debug"
  env
  echo "--- Testing"
  mvn -Djava.awt.headless=true --toolchains=toolchains.xml -Paurin-tools install
}

function mvn_deploy {
  clean
  mvn -Djava.awt.headless=true --toolchains=toolchains.xml -Paurin-tools -DskipTests install
  echo "--- Deploy"
  mvn -Djava.awt.headless=true --toolchains=toolchains.xml -Paurin-tools -DskipTests "-DaltDeploymentRepository=${MVN_REPOSITORY}" deploy
}

function usage() {
 echo "Must specify a mode"
}

case $1 in
    test)
      mvn_test
    ;;
    deploy)
     mvn_deploy
    ;;
    * )
      usage
      exit 1
esac


