#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--help" ]]; then
  printf '%s\n' 'Usage: bash /path/to/SafeNest/build.sh [Gradle task ...]' \
    'Default tasks: :app:testDirectDebugUnitTest :app:assembleDirectDebug' \
    'Requires a full JDK 17 or 21 and Android SDK platform 36. See BUILDING.md.'
  exit 0
fi

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/android"
fail() { printf 'SafeNest build could not start: %s\n' "$*" >&2; exit 1; }
[[ -f "$project_dir/settings.gradle.kts" ]] || fail 'Extract the entire SafeNest ZIP first.'

jdk_candidates=("${JAVA_HOME:-}" '/opt/android-studio/jbr' '/usr/local/android-studio/jbr' '/Applications/Android Studio.app/Contents/jbr/Contents/Home')
if [[ -x /usr/libexec/java_home ]]; then
  jdk_candidates+=("$(/usr/libexec/java_home 2>/dev/null || true)")
fi
if command -v javac >/dev/null 2>&1; then
  javac_path="$(command -v javac)"
  if command -v realpath >/dev/null 2>&1; then javac_path="$(realpath "$javac_path")"; fi
  jdk_candidates+=("$(dirname -- "$(dirname -- "$javac_path")")")
fi
jdk=''
for candidate in "${jdk_candidates[@]}"; do
  if [[ -n "$candidate" && -x "$candidate/bin/java" && -x "$candidate/bin/javac" ]]; then
    jdk="$candidate"
    break
  fi
done
[[ -n "$jdk" ]] || fail 'Install a full JDK 17 or 21 or Android Studio, then set JAVA_HOME to its JDK folder. A Java runtime alone is insufficient. See BUILDING.md.'
export JAVA_HOME="$jdk"

sdk_candidates=()
if [[ -f "$project_dir/local.properties" ]]; then
  local_sdk="$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' "$project_dir/local.properties" | head -n 1 | sed 's/\\ / /g;s/\\:/:/g;s/\\\\/\\/g')"
  sdk_candidates+=("$local_sdk")
fi
sdk_candidates+=("${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "${HOME:-}/Android/Sdk" "${HOME:-}/Library/Android/sdk")
sdk=''
for candidate in "${sdk_candidates[@]}"; do
  if [[ -n "$candidate" && -f "$candidate/platforms/android-36/android.jar" ]]; then
    sdk="$candidate"
    break
  fi
done
[[ -n "$sdk" ]] || fail 'Install Android API 36 and Android SDK Build-Tools 35.0.0 using Android Studio > Tools > SDK Manager. Set ANDROID_HOME for a custom SDK location. See BUILDING.md.'
export ANDROID_HOME="$sdk"

cd -- "$project_dir"
if [[ -f ./gradlew && -f ./gradle/wrapper/gradle-wrapper.jar ]]; then
  gradle_command=(bash ./gradlew)
elif command -v gradle >/dev/null 2>&1; then
  gradle_command=(gradle)
  printf '%s\n' 'Using installed Gradle; this project requires Gradle 8.13.'
else
  fail 'Missing Gradle wrapper. Extract the entire ZIP, including android/gradlew and android/gradle/wrapper/gradle-wrapper.jar.'
fi
if [[ $# -eq 0 ]]; then set -- :app:testDirectDebugUnitTest :app:assembleDirectDebug; fi
printf 'Project: %s\nJDK: %s\nAndroid SDK: %s\n' "$project_dir" "$jdk" "$sdk"
"${gradle_command[@]}" --no-daemon --console=plain "$@"
printf '%s\n' 'Gradle tasks completed successfully.'
for task in "$@"; do
  if [[ "$task" == ':app:assembleDirectDebug' ]]; then
    printf 'Debug APK: %s/app/build/outputs/apk/direct/debug/app-direct-debug.apk\n' "$project_dir"
    break
  fi
done
