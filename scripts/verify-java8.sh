#!/usr/bin/env bash
set -euo pipefail
logging_workspace="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$logging_workspace/scripts/java8-env.sh"
# Build exactly one baseline JAR, then load that unchanged file in both consumer runtimes.
"$logging_maven_bin" -B -f "$logging_workspace/backend-common-logging/pom.xml" clean verify
logging_jar="$logging_workspace/backend-common-logging/target/backend-common-logging-0.1.0-SNAPSHOT.jar"
logging_original_checksum="$(cksum < "$logging_jar")"
for logging_boot_version in 2.4.3 2.4.4; do
  if [[ "$logging_boot_version" != 2.4.3 ]]; then
    "$logging_maven_bin" -B -f "$logging_workspace/backend-common-logging/pom.xml" test "-Dspring-boot.version=$logging_boot_version"
  fi
  "$logging_maven_bin" -B -f "$logging_workspace/examples/insurance-crud/pom.xml" clean test "-Dspring-boot.version=$logging_boot_version"
  if [[ "$(cksum < "$logging_jar")" != "$logging_original_checksum" ]]; then
    echo 'Library JAR changed during consumer verification.' >&2; exit 1
  fi
  echo "PASS: unchanged Java 8 JAR on Spring Boot $logging_boot_version"
done
