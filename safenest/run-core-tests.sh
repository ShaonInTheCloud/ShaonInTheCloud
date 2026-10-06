#!/usr/bin/env sh
# Standalone DNS, transport, domain and signed catalog tests; no Android SDK or external downloads.
set -eu
safenest_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
safenest_main="$safenest_root/android/app/src/main/java/com/safenest/app"
safenest_test="$safenest_root/android/app/src/test/java/com/safenest/app"
safenest_java=${SAFENEST_JAVA:-java}
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && [ -z "${SAFENEST_JAVA:-}" ]; then
    safenest_java="$JAVA_HOME/bin/java"
fi
safenest_out=$(mktemp -d "${TMPDIR:-/tmp}/safenest-core.XXXXXX")
trap 'rm -rf "$safenest_out"' EXIT HUP INT TERM
"$safenest_java" -m jdk.compiler/com.sun.tools.javac.Main --release 17 -Xlint:all -d "$safenest_out" \
    "$safenest_main/DnsPacketCodec.java" "$safenest_main/DomainRules.java" "$safenest_main/CatalogVerifier.java" \
    "$safenest_main/DnsAliasInspector.java" "$safenest_main/DnsUpstreamTransport.java" \
    "$safenest_main/CommitmentRules.java" "$safenest_main/SystemScreenGuard.java" "$safenest_main/ControlScreenExit.java" "$safenest_main/WindowPackageCache.java" \
    "$safenest_test/DnsPacketCodecRegression.java" "$safenest_test/DomainRulesRegression.java" \
    "$safenest_test/DnsAliasInspectorRegression.java" "$safenest_test/DnsUpstreamTransportRegression.java" \
    "$safenest_test/CatalogVerifierRegression.java" "$safenest_test/CommitmentGuardRegression.java" "$safenest_test/ControlScreenExitRegression.java"
"$safenest_java" -cp "$safenest_out" com.safenest.app.DnsPacketCodecRegression
"$safenest_java" -cp "$safenest_out" com.safenest.app.DomainRulesRegression
"$safenest_java" -cp "$safenest_out" com.safenest.app.DnsAliasInspectorRegression
"$safenest_java" -cp "$safenest_out" com.safenest.app.DnsUpstreamTransportRegression
"$safenest_java" -cp "$safenest_out" com.safenest.app.CatalogVerifierRegression
"$safenest_java" -cp "$safenest_out" com.safenest.app.CommitmentGuardRegression
"$safenest_java" -cp "$safenest_out" com.safenest.app.ControlScreenExitRegression
printf '%s\n' 'These checks do not compile the Android app or test a device. Run the Android Gradle tests and device checks separately.'
