# Generates the dealers' voice lines with the Russian Windows voices (OneCore "Irina" = female, "Pavel" = male).
# Usage (Windows PowerShell 5.1, from anywhere):  & ".../tools/VoiceGen.ps1"
# Edit tools/voice/phrases.txt to change the texts; real recordings can replace any generated file.
param(
    [string]$FemaleVoice = 'Irina',
    [string]$MaleVoice = 'Pavel',
    [double]$Rate = 1.0
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$java = if (Test-Path 'D:\Work\Tools\jdk-21\bin\java.exe') { 'D:\Work\Tools\jdk-21\bin\java.exe' } else { 'java' }
$raw = Join-Path $root 'build\voice-raw'
$out = Join-Path $root 'src\main\resources\assets\mosvegas_casino\voice'
$linesFile = Join-Path $raw 'lines.txt'

Push-Location $root
try {
    & $java tools/VoicePack.java lines tools/voice/phrases.txt $linesFile
    if ($LASTEXITCODE -ne 0) { throw 'expanding the phrase list failed' }

    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Media.SpeechSynthesis.SpeechSynthesizer, Windows.Media.SpeechSynthesis, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
    function Await($op, [Type]$type) {
        $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
        $task.Wait(-1) | Out-Null
        $task.Result
    }
    $synth = New-Object Windows.Media.SpeechSynthesis.SpeechSynthesizer
    $all = [Windows.Media.SpeechSynthesis.SpeechSynthesizer]::AllVoices
    $voices = @{
        female = $all | Where-Object { $_.DisplayName -like "*$FemaleVoice*" } | Select-Object -First 1
        male   = $all | Where-Object { $_.DisplayName -like "*$MaleVoice*" } | Select-Object -First 1
    }
    foreach ($k in @('female', 'male')) { if ($null -eq $voices[$k]) { throw "voice '$k' not installed (Settings > Time and language > Speech)" } }
    $synth.Options.SpeakingRate = $Rate

    $count = 0
    foreach ($line in [IO.File]::ReadAllLines($linesFile, [Text.Encoding]::UTF8)) {
        $parts = $line.Split('|', 3)
        if ($parts.Count -lt 3) { continue }
        $set = $parts[0]; $key = $parts[1]; $text = $parts[2]
        $synth.Voice = $voices[$set]
        $stream = Await ($synth.SynthesizeTextToStreamAsync($text)) ([Windows.Media.SpeechSynthesis.SpeechSynthesisStream])
        $reader = New-Object Windows.Storage.Streams.DataReader($stream.GetInputStreamAt(0))
        $size = [uint32]$stream.Size
        $null = Await ($reader.LoadAsync($size)) ([uint32])
        $bytes = New-Object byte[] $size
        $reader.ReadBytes($bytes)
        $dir = Join-Path $raw $set
        New-Item -ItemType Directory -Force $dir | Out-Null
        [IO.File]::WriteAllBytes((Join-Path $dir "$key.wav"), $bytes)
        $count++
    }
    Write-Output "synthesized $count lines"

    if (Test-Path $out) { Remove-Item -Recurse -Force $out }
    & $java tools/VoicePack.java pack $raw $out
    if ($LASTEXITCODE -ne 0) { throw 'packing failed' }
} finally {
    Pop-Location
}
