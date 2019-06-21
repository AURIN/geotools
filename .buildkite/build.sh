#!/usr/bin/env bash

set -euo pipefail

function build {
  local jdk7_home="${JAVA_7_HOME}"
  mvn "-Paurin -Djdk7_home=${jdk7_home} clean compile"
}

build
