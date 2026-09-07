param([switch]$ConfirmReset)
$ErrorActionPreference = 'Stop'
if (-not $ConfirmReset) {
    throw 'Stop The Annex, then run this script with -ConfirmReset to delete the default local demo database and app-owned agenda images. This removes intake drafts, saved requests and bookings.'
}
$annexRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$annexData = [IO.Path]::GetFullPath((Join-Path $annexRoot 'data'))
if (-not $annexData.StartsWith($annexRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refusing a reset outside the project directory.'
}
$annexAttachments = [IO.Path]::GetFullPath((Join-Path $annexData 'attachments'))
foreach ($directory in @($annexData,$annexAttachments)) {
    if ((Test-Path -LiteralPath $directory) -and ((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'Refusing to reset through a linked data or attachment directory.'
    }
}
# Only the named default H2 files and UUID-named app images are eligible. Never recurse.
foreach ($name in @('annex.mv.db','annex.trace.db','annex.lock.db')) {
    $target = Join-Path $annexData $name
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target }
}
if (Test-Path -LiteralPath $annexAttachments) {
    Get-ChildItem -LiteralPath $annexAttachments -File | Where-Object {
        $_.Name -match '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(png|jpg)$'
    } | ForEach-Object {
        $imageTarget = [IO.Path]::GetFullPath($_.FullName)
        if (-not $imageTarget.StartsWith($annexAttachments + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing an attachment reset outside the default attachment directory.'
        }
        Remove-Item -LiteralPath $imageTarget
    }
}
Write-Output 'Default demo database and app-owned agenda images removed. Flyway will recreate fixtures at startup. Custom database/storage locations and other files are untouched.'
