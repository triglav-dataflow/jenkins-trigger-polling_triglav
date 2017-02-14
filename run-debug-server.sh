#!/usr/bin/env bash

echo export GRADLE_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
echo ./gradlew server

echo
echo "============================================================="
echo "  The feature of 'break point' does not work on IDEA"
echo "    see. https://issues.jenkins-ci.org/browse/JENKINS-29299"
echo "  Use Remote Debugger instead."
echo "============================================================="
echo

export GRADLE_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
./gradlew server