param(
    [Parameter(Mandatory = $true)][string]$Model,
    [string]$Rig,
    [string]$Assets = 'slimcore/src/main/resources/assets/slimvehicle',
    [string]$Compare
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$java = 'D:\Work\Tools\jdk-21\bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) {
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
}
$gson = Get-ChildItem (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson') -Recurse -Filter 'gson-*.jar' |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object Name -Descending | Select-Object -First 1
if (-not $gson) { throw 'Gson is not in the Gradle cache yet: run .\gradlew.bat build once.' }
$tool = Join-Path $PSScriptRoot 'ImportBlockbenchModel.java'
if ($Compare) {
    & $java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp $gson.FullName $tool $Model --compare $Compare
} else {
    if (-not $Rig) { throw 'Pass -Rig tools/rigs/<model>.json or -Compare <geo.json>.' }
    & $java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp $gson.FullName $tool $Model $Rig (Join-Path $root $Assets)
}
exit $LASTEXITCODE
