#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build
mvn -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=build/lib
javac --release 17 --add-modules jdk.httpserver -cp 'build/lib/*' -d build src/in/pact/*.java
exec java --add-modules jdk.httpserver -cp 'build:build/lib/*' in.pact.Main "$@"
