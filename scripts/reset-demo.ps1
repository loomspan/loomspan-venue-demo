param([switch]$ConfirmReset)
$ErrorActionPreference = 'Stop'
if (-not $ConfirmReset) {
    throw 'Stop The Annex, then run this script with -ConfirmReset to delete the default local demo database. This removes saved requests and bookings.'
}
$annexRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$annexData = [IO.Path]::GetFullPath((Join-Path $annexRoot 'data'))
if (-not $annexData.StartsWith($annexRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refusing a reset outside the project directory.'
}
# Only the named default H2 files are eligible. Never recurse or delete arbitrary attachments.
foreach ($name in @('annex.mv.db','annex.trace.db','annex.lock.db')) {
    $target = Join-Path $annexData $name
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target }
}
Write-Output 'Default demo database removed. Flyway will recreate the schema and seed records at next startup. Custom ANNEX_DATABASE_URL databases are untouched.'
