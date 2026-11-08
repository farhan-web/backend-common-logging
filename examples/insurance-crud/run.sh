#!/usr/bin/env bash
set -euo pipefail
example_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
workspace_dir="$(cd -- "$example_dir/../.." && pwd)"
if command -v mvn >/dev/null 2>&1; then
  maven_bin="$(command -v mvn)"
elif [[ -x "/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" ]]; then
  maven_bin="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
else
  echo "Maven 3.6.3+ diperlukan. Install Maven atau jalankan project lewat IntelliJ IDEA." >&2
  exit 1
fi
# Build the local JAR before Maven resolves the application's systemPath dependency.
"$maven_bin" -B -f "$workspace_dir/backend-common-logging/pom.xml" package -DskipTests
"$maven_bin" -B -f "$example_dir/pom.xml" package -DskipTests
exec java -jar "$example_dir/target/insurance-crud-0.1.0-SNAPSHOT.jar" "$@"
