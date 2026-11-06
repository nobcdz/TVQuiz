param(
    [string]$JavaHome = 'D:/Program Files/Android/Android Studio/jbr',
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$KotlinLib = 'D:/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc/lib',
    [string]$Tv = '192.168.31.151:5555',
    [int]$AdbPort = 15037,
    [switch]$SkipAppInstall
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome/bin;$env:Path"
$out = "$root/build/local"
$tools = "$Sdk/build-tools/34.0.0"
$android = "$Sdk/platforms/android-34/android.jar"
$adb = "$Sdk/platform-tools/adb.exe"
$test = "$root/build/tests"
New-Item -Force -ItemType Directory "$test/dex", "$test/assets" | Out-Null
Copy-Item data/seed.json "$test/assets/questions.json" -Force
@'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.nobcdz.tvquiz.tests">
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
    <application android:label="TVQuiz regression checks" android:debuggable="true"/>
    <instrumentation android:name="com.nobcdz.tvquiz.tests.RegressionInstrumentation" android:targetPackage="com.nobcdz.tvquiz.tests"/>
</manifest>
'@ | Set-Content -Encoding utf8 "$test/AndroidManifest.xml"
& "$JavaHome/bin/java.exe" -cp "$KotlinLib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 1.8 "-Xfriend-paths=$out/classes.jar" -classpath "$android;$KotlinLib/kotlin-stdlib.jar;$out/classes.jar" -d "$test/tests.jar" tests/RegressionInstrumentation.kt
& "$tools/d8.bat" --min-api 21 --lib $android --output "$test/dex" "$test/tests.jar" "$out/classes.jar" "$out/resources.jar" "$KotlinLib/kotlin-stdlib.jar"
& "$tools/aapt2.exe" link -I $android --manifest "$test/AndroidManifest.xml" --min-sdk-version 21 --target-sdk-version 34 -A "$test/assets" -o "$test/tests.apk"
Push-Location "$test/dex"
try { & "$JavaHome/bin/jar.exe" uf "$test/tests.apk" classes.dex } finally { Pop-Location }
& "$tools/apksigner.bat" sign --ks "$out/debug.jks" --ks-pass pass:android "$test/tests.apk"
& $adb -P $AdbPort connect $Tv
if (!$SkipAppInstall) { & $adb -P $AdbPort -s $Tv install -r app/native/app/build/outputs/apk/tv/debug/app-tv-debug.apk }
$installedTest = & $adb -P $AdbPort -s $Tv shell pm path com.nobcdz.tvquiz.tests
if (($installedTest -join "") -match "package:") {
    & $adb -P $AdbPort -s $Tv uninstall com.nobcdz.tvquiz.tests
}
& $adb -P $AdbPort -s $Tv install "$test/tests.apk"
$result = & $adb -P $AdbPort -s $Tv shell am instrument -w com.nobcdz.tvquiz.tests/com.nobcdz.tvquiz.tests.RegressionInstrumentation
$result | Write-Output
if (($result -join "\n") -notmatch 'PASS —') { throw 'TV regression checks failed' }
