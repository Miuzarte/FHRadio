# .\tool\convert.ps1 -InputFolder 'B:\Software\Fmod_Bank_Tools\wav' -OutputFolder 'A:\Miuzarte\Music\Forza Horizon 6' -Concurrency 6
param(
    [string]$InputFolder = $PWD,
    [Parameter(Mandatory)]
    [string]$OutputFolder,
    [string]$InputExt = "wav",
    [string]$OutputExt = "flac",
    [int]$Concurrency = 1,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
if (-not $ffmpeg) {
    Write-Error "ffmpeg not found in PATH"
    exit 1
}
Write-Host "ffmpeg: $($ffmpeg.Source)`n"

$InputFolder = [System.IO.Path]::GetFullPath($InputFolder)
$OutputFolder = [System.IO.Path]::GetFullPath($OutputFolder)

if (-not (Test-Path -LiteralPath $InputFolder -PathType Container)) {
    Write-Error "Input folder not found: $InputFolder"
    exit 1
}

Write-Host "Scanning: $InputFolder"
$files = Get-ChildItem -LiteralPath $InputFolder -Recurse -File -Filter "*.$InputExt"
if ($files.Count -eq 0) {
    Write-Host "No .$InputExt files found."
    exit 0
}
Write-Host "Found $($files.Count) file(s)`n"

$tasks = foreach ($file in $files) {
    $relPath = [System.IO.Path]::GetRelativePath($InputFolder, $file.FullName)
    $outPath = Join-Path $OutputFolder ([System.IO.Path]::ChangeExtension($relPath, ".$OutputExt"))
    [PSCustomObject]@{ Input = $file.FullName; Output = $outPath; Name = $file.Name }
}

if ($DryRun) {
    $i = 0
    foreach ($t in $tasks) {
        Write-Host "[$((++$i))/$($tasks.Count)] $($t.Name)"
        Write-Host "  -> $($t.Output) (dry-run)"
    }
    Write-Host "`nWould convert $($tasks.Count) file(s)."
    exit 0
}

if (-not (Test-Path -LiteralPath $OutputFolder -PathType Container)) {
    New-Item -ItemType Directory -Path $OutputFolder -Force | Out-Null
    Write-Host "Created: $OutputFolder"
}
$tasks | ForEach-Object {
    $dir = Split-Path $_.Output -Parent
    if (-not (Test-Path -LiteralPath $dir -PathType Container)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

Write-Host "Converting (concurrency=$Concurrency)...`n"

if ($Concurrency -le 1) {
    $ok = 0; $fail = 0
    foreach ($t in $tasks) {
        Write-Host "[$($ok + $fail + 1)/$($tasks.Count)] $($t.Name)"
        & ffmpeg -y -i $t.Input -c:a flac -map_metadata 0 -compression_level 8 $t.Output *>$null
        if ($LASTEXITCODE -eq 0) { $ok++ } else { Write-Warning "Failed: $($t.Name)"; $fail++ }
    }
    Write-Host "`nDone. $ok succeeded, $fail failed."
} else {
    $results = $tasks | ForEach-Object -ThrottleLimit $Concurrency -Parallel {
        $t = $_
        Write-Host "$($t.Name)"
        & ffmpeg -y -i $t.Input -c:a flac -map_metadata 0 -compression_level 8 $t.Output *>$null
        $LASTEXITCODE
    }
    $ok = ($results | Where-Object { $_ -eq 0 }).Count
    $fail = $results.Count - $ok
    Write-Host "`nDone. $ok succeeded, $fail failed."
}
