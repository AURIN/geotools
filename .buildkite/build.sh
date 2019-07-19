#!/usr/bin/env bash

set -euo pipefail

function javadoc_tools {
  cd build/maven/javadoc
  maven clean install
  cd $BUILDKITE_BUILD_CHECKOUT_PATH
}

function maven {
  mvn -T 1C -Djava.awt.headless=true -Paurin ${@}
}


echo "--- Bootstrap Build"

javadoc_tools

echo "--- Update Version"
CURRENT_VERSION=$(maven org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -Dexpression=project.version -q -DforceStdout)
NEW_VERSION="${CURRENT_VERSION}-${BUILDKITE_BUILD_NUMBER}"

maven org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion="${CURRENT_VERSION}-${BUILDKITE_BUILD_NUMBER}"

buildkite-agent meta-data set version "v${NEW_VERSION}"


echo "--- Perform Build"
maven clean install

echo "--- Deploy"
maven \
  -DaltDeploymentRepository="new_nexus::https://repo.aurin.cloud.edu.au/repository/maven-aurin-stable/" \
  org.apache.maven.plugins:maven-deploy-plugin:3.0.0-M1:deploy

