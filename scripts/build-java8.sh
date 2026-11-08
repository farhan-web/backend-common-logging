#!/usr/bin/env bash
set -euo pipefail
logging_workspace="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$logging_workspace/scripts/java8-env.sh"
exec "$logging_maven_bin" -B -f "$logging_workspace/backend-common-logging/pom.xml" clean verify "$@"
