$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ProjectRoot
$env:ANDROID_HOME = Join-Path $RepoRoot "temp\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

$Gradle = Join-Path $env:USERPROFILE ".gradle\wrapper\dists\gradle-8.11.1-all\y7uh7dypdqa88ii13rpbex4x\gradle-8.11.1\bin\gradle.bat"

Push-Location $ProjectRoot
try {
    & $Gradle :app:assembleDebug :app:assembleRelease --console=plain
    Copy-Item -Force "app\build\outputs\apk\debug\app-debug.apk" "BangBangAgro-native-latest-debug.apk"
    Copy-Item -Force "app\build\outputs\apk\release\app-release.apk" "BangBangAgro-native-latest-release.apk"
    Write-Host "Build completed:"
    Write-Host "  $ProjectRoot\BangBangAgro-native-latest-debug.apk"
    Write-Host "  $ProjectRoot\BangBangAgro-native-latest-release.apk"
}
finally {
    Pop-Location
}
