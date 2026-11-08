#!/usr/bin/env bash
set -euo pipefail
example_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
workspace_dir="$(cd -- "$example_dir/../.." && pwd)"
source "$workspace_dir/scripts/java8-env.sh"
"$logging_maven_bin" -B -f "$workspace_dir/backend-common-logging/pom.xml" package -DskipTests
"$logging_maven_bin" -B -f "$example_dir/pom.xml" package -DskipTests
exec "$logging_java_bin" -jar "$example_dir/target/insurance-crud-0.1.0-SNAPSHOT.jar" "$@"
