# Build SafeNest for Android

## Android Studio (recommended)

1. Extract the **entire ZIP**. In Android Studio choose **File > Open**, then select the inner **`SafeNest/android`** directory containing `settings.gradle.kts`.
2. Under **Tools > SDK Manager**, install **Android API 36** and **Android SDK Build-Tools 35.0.0**. Android Studio can prompt to install missing packages.
3. In **Settings > Build, Execution, Deployment > Build Tools > Gradle**, use a full **JDK 17 or JDK 21**, such as Android Studio's bundled JBR. Do not use a standalone Java runtime.
4. Let Gradle sync. Select the **app** run configuration and your emulator/phone, then click **Run**. **Sync alone does not install the updated app.**

The repository includes the official **Gradle 8.13 wrapper**. Its first run downloads Gradle and checks the pinned SHA-256 checksum. Do not upgrade Gradle or the Android Gradle plugin just because the IDE offers an update while testing this revision.

## Windows PowerShell

The `build-windows.ps1` script is in **SafeNest**, one directory above `android`. It runs unit tests and builds a debug APK. It locates the project relative to the script itself, so quoted paths containing spaces or `(1)` work.

Using the download-folder layout in the screenshots:

```powershell
Set-Location -LiteralPath 'C:\Users\User\Downloads\SafeNest-Android-first-prototype (1)\SafeNest'
powershell -NoProfile -ExecutionPolicy Bypass -File '.\build-windows.ps1'
```

Or invoke the full script path from any directory:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File 'C:\Users\User\Downloads\SafeNest-Android-first-prototype (1)\SafeNest\build-windows.ps1'
```

Use your actual extraction folder if its name differs. Entering a folder path by itself does not change PowerShell's directory; use `Set-Location` as shown.

The script looks for a JDK in `JAVA_HOME`, Android Studio's installation, and then `javac` on `PATH`. It looks for SDK 36 in `android/local.properties`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `%LOCALAPPDATA%\Android\Sdk`. For custom installations, set the variables in the current terminal before running:

```powershell
$env:JAVA_HOME = 'C:\Path\To\Your\jdk-17'
$env:ANDROID_HOME = 'C:\Path\To\Your\Android\Sdk'
```

Replace these example paths with real directories. The script never overwrites `local.properties`. If that file points to an old SDK, open the `android` folder in Android Studio to correct its SDK location.

## macOS/Linux

```bash
bash '/path/to/SafeNest/build.sh'
```

The script discovers Android Studio's JDK and usual SDK locations. For custom installations, export `JAVA_HOME` and `ANDROID_HOME` first. To run only unit tests:

```bash
bash '/path/to/SafeNest/build.sh' :app:testDebugUnitTest
```

## Managed-device setup

After installing, follow **docs/device-owner-setup.md**. VPN consent is separate from Device Owner enrollment. The in-app setup screen reports actual policy values; Android may still show a Disconnect button whose action is rejected. Version 0.3.1 requires an administrator recovery code for new managed setups.

## Build outputs

- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- JVM unit-test report: `android/app/build/reports/tests/testDebugUnitTest/index.html`

A debug APK uses a development signing key and is for testing. Build success and passing JVM tests do not verify VPN connectivity, Android Accessibility behavior, device-admin behavior, or resistance to removal. Test those on the target Android device before distribution. This project does not include release-signing credentials.

## Wrapper provenance

`gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` came from Gradle's official `gradle/gradle` repository tag `v8.13.0`. The JAR was verified against Gradle's official wrapper checksum, and `gradle-wrapper.properties` pins the distribution checksum from Gradle's official download service.

- [Gradle wrapper documentation](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
- [AGP 8.13 compatibility requirements](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Android SDK tools](https://developer.android.com/studio)
