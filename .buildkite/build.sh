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

javadoc_tools

maven clean install
