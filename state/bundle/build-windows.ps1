# Сборка APK NOCTILIS на компьютере Андрея (Windows). Ключа подписи здесь нет: собирается APK
# с отладочной подписью и отправляется на KZ, там bin/sign-apk.sh подписывает постоянным ключом.
# Нужен включённый VPN: Google не отдаёт Android-инструменты на российские адреса.
# Запуск: powershell -ExecutionPolicy Bypass -File build-windows.ps1
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$KZ = 'root@89.40.233.2'
$R = 'C:\noctilis-build'
New-Item -ItemType Directory -Force $R | Out-Null
Set-Location $R

Write-Host '== 1/5 Java 17'
if (!(Test-Path "$R\jdk\bin\java.exe")) {
    Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile jdk.zip
    Expand-Archive jdk.zip tmpjdk -Force
    Move-Item (Get-ChildItem tmpjdk)[0].FullName "$R\jdk"
    Remove-Item jdk.zip, tmpjdk -Recurse -Force
}
$env:JAVA_HOME = "$R\jdk"
$env:Path = "$R\jdk\bin;$env:Path"

Write-Host '== 2/5 Android SDK'
$env:ANDROID_HOME = "$R\sdk"; $env:ANDROID_SDK_ROOT = "$R\sdk"
if (!(Test-Path "$R\sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
    Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile clt.zip
    Expand-Archive clt.zip tmpclt -Force
    New-Item -ItemType Directory -Force "$R\sdk\cmdline-tools" | Out-Null
    Move-Item "tmpclt\cmdline-tools" "$R\sdk\cmdline-tools\latest"
    Remove-Item clt.zip, tmpclt -Recurse -Force
}
$sdk = "$R\sdk\cmdline-tools\latest\bin\sdkmanager.bat"
(1..30 | ForEach-Object { 'y' }) | & $sdk --licenses | Out-Null
& $sdk --install 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' | Out-Null

Write-Host '== 3/5 Gradle'
if (!(Test-Path "$R\gradle\bin\gradle.bat")) {
    Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.10.2-bin.zip' -OutFile gradle.zip
    Expand-Archive gradle.zip tmpg -Force
    Move-Item 'tmpg\gradle-8.10.2' "$R\gradle"
    Remove-Item gradle.zip, tmpg -Recurse -Force
}

Write-Host '== 4/5 Код с сервера'
scp "${KZ}:/root/projects/techer-app/state/bundle/noctilis-src.zip" "$R\src.zip"
if (Test-Path "$R\src") { Remove-Item "$R\src" -Recurse -Force }
Expand-Archive "$R\src.zip" "$R\src" -Force
$N = (Get-Content "$R\src\VERSION").Trim()
$env:APP_VERSION_CODE = $N
$env:APP_VERSION_NAME = "0.0.$N"
Remove-Item Env:KEYSTORE_FILE -ErrorAction SilentlyContinue
Remove-Item Env:KEYSTORE_PASS -ErrorAction SilentlyContinue

Write-Host "== 5/5 Сборка 0.0.$N"
Set-Location "$R\src"
& "$R\gradle\bin\gradle.bat" assembleRelease --no-daemon -q
if ($LASTEXITCODE -ne 0) { throw 'Сборка упала' }
scp "$R\src\app\build\outputs\apk\release\app-release.apk" "${KZ}:/root/projects/techer-app/state/incoming/noctilis-unsigned-0.0.$N.apk"
Write-Host "ГОТОВО: 0.0.$N отправлена на сервер. Напишите в чат: собрал."
