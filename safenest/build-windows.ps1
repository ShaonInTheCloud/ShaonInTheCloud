param(
    [string[]]$Task = @(':app:testDirectDebugUnitTest', ':app:assembleDirectDebug')
)

$ErrorActionPreference = 'Stop'
$projectDir = Join-Path $PSScriptRoot 'android'
$previousJava = $env:JAVA_HOME
$previousSdk = $env:ANDROID_HOME
$resultCode = 1

try {
    if (-not (Test-Path -LiteralPath (Join-Path $projectDir 'settings.gradle.kts'))) {
        throw 'Project incomplete. Extract the entire ZIP, then run the script inside the SafeNest folder.'
    }

    $javaCandidates = @($env:JAVA_HOME)
    if ($env:ProgramFiles) { $javaCandidates += (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr') }
    if ($env:LOCALAPPDATA) { $javaCandidates += (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr') }
    $compiler = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($compiler) { $javaCandidates += (Split-Path (Split-Path $compiler.Source -Parent) -Parent) }
    $jdk = $javaCandidates | Where-Object {
        $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe')) -and
        (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe'))
    } | Select-Object -First 1
    if (-not $jdk) {
        throw 'A full JDK is required. Install Android Studio, or install JDK 17 and set JAVA_HOME to its folder. A Java runtime alone is insufficient. See BUILDING.md.'
    }
    $env:JAVA_HOME = $jdk

    $sdkCandidates = @()
    $localProperties = Join-Path $projectDir 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($sdkLine) {
            $sdkCandidates += (($sdkLine -replace '^\s*sdk\.dir\s*=\s*', '').Replace('\\', '\').Replace('\:', ':').Replace('\ ', ' '))
        }
    }
    $sdkCandidates += @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    if ($env:LOCALAPPDATA) { $sdkCandidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk') }
    $sdk = $sdkCandidates | Where-Object {
        $_ -and (Test-Path -LiteralPath (Join-Path $_ 'platforms\android-36\android.jar'))
    } | Select-Object -First 1
    if (-not $sdk) {
        throw 'Android SDK 36 was not found. In Android Studio open Tools > SDK Manager, install Android API 36 and Android SDK Build-Tools 35.0.0. Set ANDROID_HOME if your SDK uses a custom location. See BUILDING.md.'
    }
    $env:ANDROID_HOME = $sdk

    $wrapper = Join-Path $projectDir 'gradlew.bat'
    $wrapperJar = Join-Path $projectDir 'gradle\wrapper\gradle-wrapper.jar'
    if ((Test-Path -LiteralPath $wrapper) -and (Test-Path -LiteralPath $wrapperJar)) {
        $gradleExecutable = $wrapper
    } else {
        $installedGradle = Get-Command gradle.bat -ErrorAction SilentlyContinue
        if (-not $installedGradle) {
            throw 'Gradle wrapper files are missing. Extract the entire ZIP again. It includes android\gradlew.bat and android\gradle\wrapper\gradle-wrapper.jar.'
        }
        $gradleExecutable = $installedGradle.Source
        Write-Host 'Using installed Gradle; this project requires Gradle 8.13.'
    }

    Write-Host "Project: $projectDir"
    Write-Host "JDK: $jdk"
    Write-Host "Android SDK: $sdk"
    Push-Location -LiteralPath $projectDir
    try {
        & $gradleExecutable --no-daemon --console=plain @Task
        $resultCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($resultCode -eq 0) {
        Write-Host 'Gradle tasks completed successfully.'
        if ($Task -contains ':app:assembleDirectDebug') {
            Write-Host ('Debug APK: ' + (Join-Path $projectDir 'app\build\outputs\apk\direct\debug\app-direct-debug.apk'))
        }
    } else {
        Write-Host 'Build failed. Share the first Gradle error above; a failed build has not produced a verified new APK.'
    }
} catch {
    Write-Host ('SafeNest build could not start: ' + $_.Exception.Message)
    $resultCode = 1
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_HOME = $previousSdk
}
exit $resultCode
