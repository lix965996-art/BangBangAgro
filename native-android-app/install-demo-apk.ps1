$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ProjectRoot
$Adb = Join-Path $RepoRoot "temp\android-sdk\platform-tools\adb.exe"
$Apk = Join-Path $ProjectRoot "BangBangAgro-native-demo-release.apk"

if (!(Test-Path $Adb)) {
    throw "adb.exe not found: $Adb"
}

if (!(Test-Path $Apk)) {
    throw "APK not found: $Apk"
}

& $Adb devices
& $Adb install -r $Apk
