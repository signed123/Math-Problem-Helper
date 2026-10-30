#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"
if [[ -z "${JAVA_HOME:-}" && -d "$project_dir/../.toolchains/jdk" ]]; then
  export JAVA_HOME="$project_dir/../.toolchains/jdk"
fi
gradle_args=()
if [[ -f /etc/ssl/certs/java/cacerts ]]; then
  gradle_args+=("-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts")
fi
if [[ -n "${HTTPS_PROXY:-}" ]]; then
  read -r proxy_host proxy_port < <(python3 - <<'PY'
import os,urllib.parse
p=urllib.parse.urlparse(os.environ['HTTPS_PROXY'])
print(p.hostname or '',p.port or 8080)
PY
  )
  if [[ -n "$proxy_host" ]]; then
    gradle_args+=("-Dhttps.proxyHost=$proxy_host" "-Dhttps.proxyPort=$proxy_port")
  fi
fi
if [[ $# -eq 0 ]]; then
  set -- :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
fi
exec ./gradlew --no-daemon "${gradle_args[@]}" "$@"
