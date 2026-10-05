#!/usr/bin/env bash
set -euo pipefail
proof_root="$(cd -- "$(dirname -- "$0")" && pwd)"
mkdir -p "$proof_root/rundir" "$proof_root/profile" "$proof_root/cache"
cd "$proof_root/rundir"
export XDG_CACHE_HOME="$proof_root/cache"
proof_java=java
if [[ -n "${JAVA_HOME:-}" ]]; then proof_java="$JAVA_HOME/bin/java"; fi
proof_platform_opts=()
if [[ "$(uname -s)" == Darwin ]]; then proof_platform_opts=(-XstartOnFirstThread); fi
exec "$proof_java" -Xms64m -Xmx512m --add-opens java.base/java.util=ALL-UNNAMED \
    "${proof_platform_opts[@]}" "-Duser.home=$proof_root/profile" -cp "$proof_root/RemixedDungeon.jar" \
    com.nyrds.pixeldungeon.desktop.AbilityProofLauncher --windowed --nosound --debug "$@"
