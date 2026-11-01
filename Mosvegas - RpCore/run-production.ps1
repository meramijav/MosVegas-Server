param([ValidateSet('client','test')][string]$Mode = 'client', [switch]$WithTacz)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
# Windows PowerShell 5.1 turns stderr lines of a native program into errors once output is redirected, and 'Stop'
# would abort on the first JVM warning. The exit code decides instead.
function Invoke-Native([string]$FilePath, [string[]]$ArgumentList) {
    $ErrorActionPreference = 'Continue'
    & $FilePath @ArgumentList
}
if (!$env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma' }
$cache = Join-Path $PSScriptRoot '.gradle-home\caches\modules-2\files-2.1'
function CachedJar([string]$relative) {
    $file = Get-ChildItem -LiteralPath (Join-Path $cache $relative) -Recurse -Filter '*.jar' |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
    if (!$file) { throw "Не найдена библиотека $relative. Сначала выполните dev.ps1 -Mode build." }
    return $file.FullName
}
$classPath = @((Get-Content -LiteralPath '.gradle/loom-cache/remapClasspath.txt' -Raw -Encoding UTF8).Trim().Split(';') | Where-Object { $_ -notmatch 'net.fabricmc.fabric-api' })
$classPath += CachedJar 'net.fabricmc\fabric-loader\0.16.14'
$classPath += CachedJar 'net.fabricmc\sponge-mixin\0.15.5+mixin.0.8.7'
foreach ($module in @('asm','asm-analysis','asm-commons','asm-tree','asm-util')) { $classPath += CachedJar "org.ow2.asm\$module\9.6" }
$classPath += @(Get-ChildItem -LiteralPath (Join-Path $cache 'org.lwjgl') -Recurse -Filter '*-natives-windows.jar' | Select-Object -ExpandProperty FullName)
$runFolder = Join-Path $PSScriptRoot $(if ($Mode -eq 'test') { 'run-smoke-production' } else { 'run-zone-tests' })
$modFolder = Join-Path $runFolder 'mods'
New-Item -ItemType Directory -Force -Path $modFolder | Out-Null
$version = ((Get-Content -LiteralPath 'gradle.properties' | Where-Object { $_ -match '^mod_version=' }) -split '=',2)[1]
# A previous version left next to the new one would load the core twice.
Get-ChildItem -LiteralPath $modFolder -Filter 'mosvegas-core-*.jar' | Remove-Item -Force
Copy-Item -LiteralPath "build/libs/mosvegas-core-$version.jar" -Destination $modFolder
Copy-Item -LiteralPath (CachedJar 'net.fabricmc.fabric-api\fabric-api\0.97.3+1.20.4') -Destination $modFolder
if ($WithTacz) {
    if (!(Test-Path -LiteralPath '.tools/tacz-runtime/tacz.jar')) { throw 'Подготовьте .tools/tacz-runtime: см. TEST_REPORT.md.' }
    Get-ChildItem -LiteralPath '.tools/tacz-runtime' -Filter '*.jar' | Where-Object { $_.Name -notlike 'mae-*' } | Copy-Item -Destination $modFolder
}
# Production mappings avoid the TaCZ Mojang/Yarn renderer-name collision in development remapping.
$vm = @('-Xmx2G', '-Dfile.encoding=UTF-8', '-Dfabric.development=false', "-Djdk.net.unixdomain.tmpdir=$env:WINDIR\Temp", '-cp', ($classPath -join ';'))
if ($Mode -eq 'test') {
    Copy-Item -LiteralPath "build/libs/mosvegas-core-$version-smoke.jar" -Destination $modFolder
    Copy-Item -LiteralPath 'run-smoke/eula.txt','run-smoke/server.properties' -Destination $runFolder
    $resultFile = Join-Path $runFolder 'smoke-result.txt'
    if (Test-Path -LiteralPath $resultFile) { Remove-Item -LiteralPath $resultFile }
    # A loading error goes to the log and ends the run instead of waiting on Fabric's error window.
    $vm += '-Djava.awt.headless=true'
    $mainArgs = @('net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui')
} else {
    $assets = Join-Path $PSScriptRoot '.gradle-home\caches\fabric-loom\assets'
    $mainArgs = @('net.fabricmc.loader.impl.launch.knot.KnotClient', '--username','MosvegasTester', '--version','1.20.4',
        '--gameDir',$runFolder, '--assetsDir',$assets, '--assetIndex','1.20.4-12', '--accessToken','0',
        '--uuid','d7c5a1d1e23d4d72b66be7d6c0c89833', '--userType','legacy', '--width','1280', '--height','720')
}
# Java argfiles keep the long classpath and Unicode paths out of the Windows command-length limit.
$argFile = Join-Path $runFolder 'java-args.txt'
$quoted = @($vm + $mainArgs) | ForEach-Object { '"' + $_.Replace('\','/').Replace('"','\"') + '"' }
$javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
# Java decodes argfiles in sun.jnu.encoding (Cp1251 on Russian Windows), not in UTF-8.
$encodingLine = Invoke-Native $javaExe '-XshowSettings:properties', '-version' 2>&1 | ForEach-Object { "$_" } | Select-String 'sun.jnu.encoding =' | Select-Object -First 1
$encodingName = if ($encodingLine) { ($encodingLine.Line -split '=',2)[1].Trim() } else { 'UTF-8' }
# GetEncoding('UTF-8') writes a BOM, which Java would read as part of the first argument.
$argEncoding = if ($encodingName -match '^Cp(\d+)$') { [Text.Encoding]::GetEncoding([int]$Matches[1]) }
    elseif ($encodingName -match '^UTF-?8$') { New-Object Text.UTF8Encoding $false }
    else { [Text.Encoding]::GetEncoding($encodingName) }
[IO.File]::WriteAllLines($argFile, $quoted, $argEncoding)
Push-Location -LiteralPath $runFolder
try {
    Invoke-Native $javaExe "@$argFile"
    if ($LASTEXITCODE -ne 0) { throw "Minecraft завершился с кодом $LASTEXITCODE" }
    if ($Mode -eq 'test') {
        $result = Get-Content -LiteralPath 'smoke-result.txt' -Raw -Encoding UTF8
        if (!$result.StartsWith('ServerSmokeTest:')) { throw $result }
        Write-Output $result
    }
} finally { Pop-Location }
