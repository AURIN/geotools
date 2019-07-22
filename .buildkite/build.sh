#!/usr/bin/env bash

set -euo pipefail

MVN_REPOSITORY="new_nexus::default::https://repo.aurin.cloud.edu.au/repository/maven-aurin-geotools"
MVN_EVALUATE_PLUGIN="org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate"
BUILDKITE_BUILD_NUMBER="${BUILDKITE_BUILD_NUMBER:-1}"

function javadoc_tools {
  pushd build/maven/javadoc
  maven clean install
  popd
}

function maven {
  mvn -U -Djava.awt.headless=true -Paurin "${@}"
}

function set_version {
  echo "--- Update Version"

  local build_number
  build_number="${BUILDKITE_BUILD_NUMBER}"

  local current_version
  current_version="$(maven ${MVN_EVALUATE_PLUGIN} -Dexpression=project.version -q -DforceStdout)"

  local new_version
  new_version="${current_version}"

  #-${BUILDKITE_BUILD_NUMBER}"
  #maven org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion="${CURRENT_VERSION}-${BUILDKITE_BUILD_NUMBER}"
  buildkite-agent meta-data set version "v${new_version}"
}

function mvn_bootstrap {
  echo "--- Bootstrap Build"
  set_version
  javadoc_tools
}

function mvn_clean {
  echo "--- cleaning"
  maven clean
}

function mvn_compile {
  echo "--- Building"
  maven compile
}

function mvn_test {
  echo "--- Testing"
  maven test
}

function mvn_deploy {
  local version
  version=$(buildkite-agent meta-data get version)
  echo "--- Deploy ${version}"
  maven -DskipTests "-DaltDeploymentRepository=${MVN_REPOSITORY}" deploy
}

function usage() {
 echo "Must specify a mode"
}

case $1 in
    test)
      mvn_clean
      mvn_bootstrap
      mvn_compile
      mvn_test
    ;;
    deploy)
      mvn_clean
      mvn_bootstrap
      mvn_compile
      mvn_deploy
    ;;
    * )
      usage
      exit 1
esac


