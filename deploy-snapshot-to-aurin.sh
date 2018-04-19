#!/usr/bin/env bash
mvn -Paurin deploy -DskipTests --batch-mode -DaltDeploymentRepository=aurin-nexus::default::https://mvn.aurin.org.au/nexus/content/repositories/snapshots
