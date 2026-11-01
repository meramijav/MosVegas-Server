param([string]$Task='build')
$ErrorActionPreference='Stop'
$javaRoot=@('D:\Work\Tools\jdk-21',$env:JAVA_HOME,(Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-delta\windows\java-runtime-delta'))|Where-Object{$_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe'))}|Select-Object -First 1
if(!$javaRoot){throw 'Java 21 not found: install it to D:\Work\Tools\jdk-21 or set JAVA_HOME to a JDK 21.'}
$env:JAVA_HOME=$javaRoot
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp -Dfile.encoding=UTF-8'
Push-Location (Split-Path $PSScriptRoot -Parent)
try { & .\gradlew.bat $Task --no-daemon --console=plain; if($LASTEXITCODE -ne 0){throw "Gradle exited $LASTEXITCODE"} } finally {Pop-Location}
