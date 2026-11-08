#!/usr/bin/env bash
# Source this file from build/run scripts. Never override an explicitly selected JDK.
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  logging_jdk8_home="$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)"
  if [[ -n "$logging_jdk8_home" ]]; then export JAVA_HOME="$logging_jdk8_home"; fi
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  logging_java_bin="$JAVA_HOME/bin/java"
else
  logging_java_bin="$(command -v java || true)"
fi
if [[ -z "$logging_java_bin" || ! -x "$logging_java_bin" ]]; then
  echo 'JDK 8 diperlukan. Set JAVA_HOME ke lokasi JDK 8.' >&2; exit 1
fi
logging_java_version="$("$logging_java_bin" -version 2>&1)"
if [[ "$logging_java_version" != *'version "1.8.'* ]]; then
  echo 'Baseline build/run project ini adalah JDK 8. Set JAVA_HOME ke JDK 8.' >&2; exit 1
fi
if [[ -n "${MAVEN_BIN:-}" ]]; then
  logging_maven_bin="$MAVEN_BIN"
elif command -v mvn >/dev/null 2>&1; then
  logging_maven_bin="$(command -v mvn)"
elif [[ -x '/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn' ]]; then
  logging_maven_bin='/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn'
else
  echo 'Maven 3.6.3+ (3.x) diperlukan; set MAVEN_BIN bila Maven tidak ada di PATH.' >&2; exit 1
fi
logging_maven_version="$("$logging_maven_bin" -version 2>&1)" || {
  echo 'Maven harus bisa berjalan pada JDK 8. Gunakan Maven 3.x melalui MAVEN_BIN.' >&2; exit 1;
}
if [[ "$logging_maven_version" != *'Apache Maven 3.'* ]]; then
  echo 'Gunakan Maven 3.x untuk build JDK 8, bukan Maven 4.' >&2; exit 1
fi
