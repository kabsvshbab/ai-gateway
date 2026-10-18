#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
if [ ! -f "$DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
    mkdir -p "$DIR/gradle/wrapper"
    curl -sL https://github.com/gradle/gradle-distributions/releases/download/v8.10.2/gradle-8.10.2-bin.zip -o /tmp/gradle.zip
    unzip -o /tmp/gradle.zip -d /tmp/gradle-install
    cp /tmp/gradle-install/gradle-8.10.2/lib/plugins/gradle-wrapper-8.10.2.jar "$DIR/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null || true
fi
exec java -jar "$DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
