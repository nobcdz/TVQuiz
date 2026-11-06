param(
    [string]$JavaHome = 'D:/Program Files/Android/Android Studio/jbr',
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$KotlinLib = 'D:/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc/lib'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome/bin;$env:Path"
$module = "$root/app/native/app"
$out = "$root/build/local"
$assets = "$out/assets"
$tools = "$Sdk/build-tools/34.0.0"
$android = "$Sdk/platforms/android-34/android.jar"
$apk = "$module/build/outputs/apk/tv/debug/app-tv-debug.apk"
$resolvedAssets = [IO.Path]::GetFullPath($assets)
$resolvedBuild = [IO.Path]::GetFullPath($out)
if ((Split-Path $resolvedAssets -Parent) -ne $resolvedBuild) { throw 'Asset cleanup must stay within the local build directory' }
if (Test-Path -LiteralPath $resolvedAssets) { Remove-Item -LiteralPath $resolvedAssets -Recurse -Force }
New-Item -Force -ItemType Directory "$out/dex", "$out/generated/com/nobcdz/tvquiz", "$assets/web", "$assets/admin", (Split-Path $apk) | Out-Null
Copy-Item app/web/* "$assets/web" -Recurse -Force
Copy-Item admin/* "$assets/admin" -Recurse -Force
Copy-Item data/seed.json "$assets/questions.json" -Force
New-Item -Force -ItemType Directory "$assets/web/assets/vendor/katex", "$assets/web/assets/js" | Out-Null
Copy-Item shared/katex/* "$assets/web/assets/vendor/katex" -Recurse -Force
Copy-Item shared/math.js "$assets/web/assets/js/math.js" -Force
Copy-Item shared/math.js "$assets/admin/assets/math.js" -Force
@'
package com.nobcdz.tvquiz
object BuildConfig {
    const val DEBUG = true
    const val FLAVOR = "tv"
    const val VERSION_NAME = "0.3.35"
}
'@ | Set-Content -Encoding utf8 "$out/generated/com/nobcdz/tvquiz/BuildConfig.kt"
$manifest = (Get-Content "$module/src/main/AndroidManifest.xml" -Raw).
    Replace('<manifest ', '<manifest package="com.nobcdz.tvquiz" ').
    Replace('${applicationId}', 'com.nobcdz.tvquiz').
    Replace('android:allowBackup="false"', 'android:allowBackup="false" android:debuggable="true"')
$manifest | Set-Content -Encoding utf8 "$out/AndroidManifest.xml"
& "$tools/aapt2.exe" compile --dir "$module/src/main/res" -o "$out/res.zip"
& "$tools/aapt2.exe" link -I $android --manifest "$out/AndroidManifest.xml" --java "$out/generated" --min-sdk-version 21 --target-sdk-version 34 --version-code 45 --version-name 0.3.35 -A $assets "$out/res.zip" -o "$out/base.apk"
# Windows aapt2 may retain backslashes in nested asset entry names.
# AssetManager expects forward slashes on the Android device.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open("$out/base.apk", [IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($entry in @($archive.Entries)) {
        if (-not $entry.FullName.Contains('\')) { continue }
        $name = $entry.FullName.Replace('\', '/')
        $buffer = [IO.MemoryStream]::new()
        $inputStream = $entry.Open()
        try { $inputStream.CopyTo($buffer) } finally { $inputStream.Dispose() }
        $entry.Delete()
        $outputStream = $archive.CreateEntry($name).Open()
        try { $buffer.Position = 0; $buffer.CopyTo($outputStream) }
        finally { $outputStream.Dispose(); $buffer.Dispose() }
    }
} finally { $archive.Dispose() }
& "$JavaHome/bin/javac.exe" -source 8 -target 8 -classpath $android -d "$out/generated-classes" "$out/generated/com/nobcdz/tvquiz/R.java"
& "$JavaHome/bin/jar.exe" cf "$out/resources.jar" -C "$out/generated-classes" .
$sources = Get-ChildItem "$module/src/main/java", "$out/generated" -Recurse -Filter *.kt | Select-Object -ExpandProperty FullName
& "$JavaHome/bin/java.exe" -cp "$KotlinLib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 1.8 -classpath "$android;$KotlinLib/kotlin-stdlib.jar;$out/resources.jar" -d "$out/classes.jar" $sources
if ($LASTEXITCODE -ne 0) { throw 'Kotlin compilation failed' }
& "$tools/d8.bat" --release --min-api 21 --lib $android --output "$out/dex" "$out/classes.jar" "$out/resources.jar" "$KotlinLib/kotlin-stdlib.jar"
Push-Location "$out/dex"
try { & "$JavaHome/bin/jar.exe" uf "$out/base.apk" (Get-ChildItem -Filter '*.dex' -Name) } finally { Pop-Location }
& "$tools/zipalign.exe" -f 4 "$out/base.apk" "$out/aligned.apk"
if (-not (Test-Path "$out/debug.jks")) {
    $previous = "$root/build/prototype-app/build/manual/debug.jks"
    if (Test-Path $previous) { Copy-Item $previous "$out/debug.jks" }
    else { & "$JavaHome/bin/keytool.exe" -genkeypair -keystore "$out/debug.jks" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US' }
}
& "$tools/apksigner.bat" sign --ks "$out/debug.jks" --ks-pass pass:android --out $apk "$out/aligned.apk"
& "$tools/apksigner.bat" verify --min-sdk-version 21 --verbose $apk
Write-Output $apk






