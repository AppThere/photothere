#!/usr/bin/env sh
# Run the app's JVM tests (the coverage test, the parity checks, the dialogs and panels).
#   scripts/test.sh                              all of :app:jvmTest
#   scripts/test.sh --tests '*PointerGapsTest*'  extra arguments go to Gradle
#
# Finds Gradle in this order: $GRADLE (a path to gradle or a gradlew), ./gradlew, ../gradlew, `gradle` on PATH.
# Use Gradle 8.14 or 9.1 with JDK 21 (see Loki Text's playbook). OFFLINE=1 adds --offline.
set -eu
cd "$(dirname "$0")/.."
if [ -n "${GRADLE:-}" ]; then g="$GRADLE"
elif [ -x ./gradlew ]; then g=./gradlew
elif [ -x ../gradlew ]; then g=../gradlew
elif command -v gradle >/dev/null 2>&1; then g=gradle
else
    echo "scripts/test.sh: no Gradle found. Install Gradle 8.14 or 9.1, or set GRADLE to a gradle or gradlew path." >&2
    exit 127
fi
offline=""
[ "${OFFLINE:-}" = 1 ] && offline="--offline"
exec "$g" $offline -p . :app:jvmTest "$@"
