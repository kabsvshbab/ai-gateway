#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE_VERSION="8.10.2"
GRADLE_HOME="$DIR/.gradle-local"

if [ ! -f "$GRADLE_HOME/gradle-$GRADLE_VERSION/bin/gradle" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    mkdir -p "$GRADLE_HOME"
    curl -sL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o /tmp/gradle.zip
    unzip -q /tmp/gradle.zip -d "$GRADLE_HOME"
    rm /tmp/gradle.zip
fi

exec "$GRADLE_HOME/gradle-$GRADLE_VERSION/bin/gradle" "$@"
