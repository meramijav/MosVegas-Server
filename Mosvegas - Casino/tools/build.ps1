param([string]$Task = 'build', [string[]]$Extra = @())
$ErrorActionPreference = 'Continue'
$candidates = @('D:\Work\Tools\jdk-21', $env:JAVA_HOME, (Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-delta\windows\java-runtime-delta'))
$javaRoot = $candidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe')) } | Select-Object -First 1
if (!$javaRoot) { throw 'JDK 21 not found. Set JAVA_HOME to a JDK 21.' }
$env:JAVA_HOME = $javaRoot
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp -Dfile.encoding=UTF-8'
$root = Split-Path $PSScriptRoot -Parent
Push-Location -LiteralPath $root
[Environment]::CurrentDirectory = $root
try {
    & (Join-Path $root 'gradlew.bat') $Task @Extra --console=plain 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
} finally { Pop-Location }
if ($code -ne 0) { Write-Host "Gradle exited $code" }
exit $code
