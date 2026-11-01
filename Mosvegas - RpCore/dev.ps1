param(
    [ValidateSet('build', 'test', 'client')][string]$Mode = 'build',
    [switch]$Offline,
    [switch]$WithTacz,
    [string]$JavaDirectory
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
# Windows PowerShell 5.1 turns stderr lines of a native program into errors once output is redirected, and 'Stop'
# would abort on the first JVM warning ("Picked up JAVA_TOOL_OPTIONS" below). The exit code decides instead.
function Invoke-Native([string]$FilePath, [string[]]$ArgumentList) {
    $ErrorActionPreference = 'Continue'
    & $FilePath @ArgumentList
}
if (!$JavaDirectory) {
    $launcherJava = Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma'
    $JavaDirectory = if (Test-Path -LiteralPath (Join-Path $launcherJava 'bin\javac.exe')) { $launcherJava } else { $env:JAVA_HOME }
}
if (!$JavaDirectory -or !(Test-Path -LiteralPath (Join-Path $JavaDirectory 'bin\javac.exe'))) {
    throw 'Укажите Java 17 JDK: -JavaDirectory <папка JDK>, либо установите JAVA_HOME.'
}
$env:JAVA_HOME = $JavaDirectory
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-home'
# This Windows JDK otherwise fails to establish a local Unix-domain pipe for Gradle.
$pipeTemp = Join-Path $env:WINDIR 'Temp'
if ($env:JAVA_TOOL_OPTIONS -notmatch 'jdk.net.unixdomain.tmpdir') {
    $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' "-Djdk.net.unixdomain.tmpdir=' + $pipeTemp + '"').Trim()
}
$arguments = @()
if ($Offline) { $arguments += '--offline' }
if ($WithTacz) {
    if (!(Test-Path -LiteralPath '.tools\tacz-runtime\tacz.jar')) { throw 'Нет тестового TaCZ: подготовьте .tools/tacz-runtime по TEST_REPORT.md.' }
}
switch ($Mode) {
    'build' { $arguments += 'build' }
    'client' { $arguments += 'build' }
    'test' {
        New-Item -ItemType Directory -Force -Path 'run-smoke' | Out-Null
        if (!(Test-Path -LiteralPath 'run-smoke/eula.txt')) { Set-Content -LiteralPath 'run-smoke/eula.txt' -Encoding ascii -Value 'eula=true' }
        if (!(Test-Path -LiteralPath 'run-smoke/server.properties')) {
            Set-Content -LiteralPath 'run-smoke/server.properties' -Encoding ascii -Value @(
                'level-name=zone-smoke', 'level-type=minecraft:flat', 'generate-structures=false',
                'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}',
                'spawn-protection=0', 'online-mode=false', 'server-ip=127.0.0.1', 'server-port=0',
                'view-distance=2', 'simulation-distance=2', 'sync-chunk-writes=false', 'max-tick-time=120000'
            )
        }
        $arguments += $(if ($WithTacz) { @('build', 'remapSmokeJar') } else { @('build', '-Psmoke', 'runSmokeServer') })
    }
}
Invoke-Native '.\gradlew.bat' $arguments
if ($LASTEXITCODE -ne 0) { throw "Gradle завершился с кодом $LASTEXITCODE" }
if ($Mode -eq 'client') { & .\run-production.ps1 -Mode client -WithTacz:$WithTacz }
elseif ($WithTacz -and $Mode -eq 'test') { & .\run-production.ps1 -Mode test -WithTacz }
