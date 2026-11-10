param([string[]]$Only)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$packTree=Get-Content -LiteralPath (Join-Path $project '.work/official-pack-tree.json') -Raw | ConvertFrom-Json
$coreTree=Get-Content -LiteralPath (Join-Path $project '.work/immersive-tree.json') -Raw | ConvertFrom-Json
$pack='src/main/resources/assets/mtsofficialpack/sounds/'
$core='mccore/src/main/resources/assets/mts/sounds/'
$selection=[ordered]@{
 engine_loop='engineamci4_running'; engine_low='engineamci4_low'; engine_mid='engineamci4_mid'; engine_high='engineamci4_high';
 engine_start='engineamci4_starting'; engine_stop='engineamci4_stopping'; exhaust_pop='engineamci4_sputter';
 door_open='doorcaropen'; door_close='doorcarclose'; hood_open='hoodcaropen'; hood_close='hoodcarclose';
 hatch_open='glovebox_open0'; hatch_close='glovebox_close0'; gear_shift='gearshiftup';
 handbrake_on='carhandbrakeup'; handbrake_off='carhandbrakedown'; lock='switch_off';
 siren='siren'; suspension_bump='wheelimpact_low0'; collision='wheelimpact_hard0';
 tyre_squeal='wheel_skidding'; road_roll='wheel_roll';
 v8_idle='enginefordfe428_idle'; v8_low='enginefordfe428_low'; v8_mid='enginefordfe428_mediumwell'; v8_high='enginefordfe428_high';
 v8_start='enginefordfe428_starting';
 v8_idle_inside='enginefordfe428_idle_inside'; v8_low_inside='enginefordfe428_low_inside'; v8_mid_inside='enginefordfe428_mediumwell_inside';
 v8_high_inside='enginefordfe428_high_inside'; v8_start_inside='enginefordfe428_starting_inside';
 g63_door_open='doorcaropen2'; g63_door_close='doorcarclose2alt'; g63_trunk_close='doorcarclose2';
 g63_hood_open='hoodcaropen1'; g63_hood_close='hoodcarclose4'; g63_hatch_open='glovebox_skyhawk_open'; g63_hatch_close='glovebox_skyhawk_close';
 g63_shift_up='gearshiftup2'; g63_shift_down='gearshiftdown3'; g63_handbrake_on='buttonpush'; g63_handbrake_off='buttonrelease'; g63_lock='bwwp';
 l9_door_open='doorplaneopen2'; l9_door_close='hoodcarclose2'; l9_hood_open='doorplaneopen'; l9_hood_close='hoodcarclose3';
 l9_trunk_open='gearhydraulics_start'; l9_trunk_close='gearhydraulics_end'; l9_hatch_open='interact_hatchopen'; l9_hatch_close='interact_hatchclose';
 l9_shift_up='switch_on'; l9_shift_down='triggerclick'; l9_handbrake_on='planebrakepush1'; l9_handbrake_off='planebrakerelease1'; l9_lock='beep'
}
$destination=Join-Path $project 'slimcore/src/main/resources/assets/slimvehicle/sounds'
$manifest=Join-Path $project 'slimcore/src/main/resources/immersive-sound-sources.json'
$kept=@{}
if($Only){
 foreach($record in (Get-Content -LiteralPath $manifest -Raw -Encoding utf8 | ConvertFrom-Json)){ $kept[$record.event]=$record }
}
function fetch([string]$event,[string]$url,[string]$blob){
 $out=Join-Path $destination ($event+'.ogg')
 Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $out
 $bytes=[IO.File]::ReadAllBytes($out)
 if([Text.Encoding]::ASCII.GetString($bytes,0,4) -ne 'OggS'){throw "Invalid OGG $out"}
 $record=[ordered]@{sha256=(Get-FileHash -LiteralPath $out -Algorithm SHA256).Hash.ToLower();source=$url;event=$event}
 if($blob){$record.upstreamGitBlob=$blob}
 return [pscustomobject]$record
}
$records=@()
foreach($entry in $selection.GetEnumerator()){
 if($Only -and $Only -notcontains $entry.Key){ if($kept.ContainsKey($entry.Key)){$records+=$kept[$entry.Key]}; continue }
 $path=$pack+$entry.Value+'.ogg'
 $blob=$packTree.tree | Where-Object path -eq $path
 if(!$blob){throw "Missing official sound $path"}
 $records+=fetch $entry.Key ('https://raw.githubusercontent.com/DonBruce64/MTSOfficialPack/'+$packTree.sha+'/'+$path) $blob.sha
}
if($Only -and $Only -notcontains 'tyre_blowout'){ $records+=$kept['tyre_blowout'] }
else { $records+=fetch 'tyre_blowout' ('https://raw.githubusercontent.com/DonBruce64/MinecraftTransportSimulator/'+$coreTree.sha+'/'+$core+'wheel_blowout.ogg') $null }
[IO.File]::WriteAllText($manifest,(ConvertTo-Json @($records) -Depth 5),(New-Object Text.UTF8Encoding($false)))
$java='D:\Work\Tools\jdk-21\bin\java.exe'
if(-not (Test-Path -LiteralPath $java)){ $java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin\java.exe'}else{'java'} }
$gson=Get-ChildItem (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson') -Recurse -Filter 'gson-*.jar' |
 Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object Name -Descending | Select-Object -First 1
if(-not $gson){throw 'Gson is not in the Gradle cache yet: run .\gradlew.bat build once.'}
$sndfile=Join-Path $project '.tools/python/_soundfile_data/libsndfile_x64.dll'
$ErrorActionPreference='Continue'
& $java --enable-preview --source 21 --enable-native-access=ALL-UNNAMED '-Dfile.encoding=UTF-8' -cp $gson.FullName (Join-Path $PSScriptRoot 'DownmixSounds.java') $sndfile $destination $manifest (Join-Path $project '.work/immersive-original-audio')
exit $LASTEXITCODE
