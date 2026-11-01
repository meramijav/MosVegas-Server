param(
    [Parameter(Mandatory=$true)][string]$PreviousSources,
    [string]$ConfigDirectory=(Join-Path $PSScriptRoot '../run/client/config/mosvegas_items/consumables')
)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$oldSources=[IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($PreviousSources))
try {
    foreach($id in @('beer','energy_drink','cigarette')) {
        $file=Join-Path $ConfigDirectory ($id+'.json')
        if (!(Test-Path -LiteralPath $file)) { continue }
        $current=Get-Content -LiteralPath $file -Raw | ConvertFrom-Json
        if ($current.id -ne $id) { continue }
        $entry=$oldSources.GetEntry("src/main/resources/data/mosvegas_items/consumables/$id.json")
        if (!$entry) { throw "Missing previous example: $id" }
        $reader=[IO.StreamReader]::new($entry.Open())
        try { $previous=$reader.ReadToEnd() | ConvertFrom-Json }
        finally { $reader.Dispose() }
        $newFile=Join-Path $PSScriptRoot "../src/main/resources/data/mosvegas_items/consumables/$id.json"
        $updated=Get-Content -LiteralPath $newFile -Raw | ConvertFrom-Json
        $changed=@()
        foreach($property in $updated.PSObject.Properties) {
            if ($property.Name -notin @('uses','sound','start_sound','finish_sound','sound_interval','sound_volume','consume_in_creative')) { continue }
            $existing=$current.PSObject.Properties[$property.Name]
            $old=$previous.PSObject.Properties[$property.Name]
            if (!$existing -or ($old -and $existing.Value -eq $old.Value)) {
                if (!$existing -or $existing.Value -ne $property.Value) {
                    $current | Add-Member -NotePropertyName $property.Name -NotePropertyValue $property.Value -Force
                    $changed+=$property.Name
                }
            }
        }
        if ($changed.Count -gt 0) {
            $json=$current | ConvertTo-Json -Depth 32
            [IO.File]::WriteAllText([IO.Path]::GetFullPath($file),$json+"`n",[Text.UTF8Encoding]::new($false))
            Write-Output ("Updated {0}: {1}" -f $id,($changed -join ', '))
        }
    }
} finally { $oldSources.Dispose() }
