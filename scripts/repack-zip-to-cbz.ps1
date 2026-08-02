[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Dir,

    [switch]$Recurse,

    [switch]$Force,

    [switch]$RemoveSource
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$resolvedDir = (Resolve-Path -LiteralPath $Dir).Path
if (-not (Test-Path -LiteralPath $resolvedDir -PathType Container)) {
    throw "Not a directory: $resolvedDir"
}

$searchOptions = @{
    LiteralPath = $resolvedDir
    File        = $true
    Filter      = '*.zip'
}
if ($Recurse) {
    $searchOptions.Recurse = $true
}

$sourceFiles = @(Get-ChildItem @searchOptions)
$converted = 0
$skipped = 0
$failed = 0

foreach ($sourceFile in $sourceFiles) {
    $targetPath = [System.IO.Path]::ChangeExtension($sourceFile.FullName, '.cbz')
    if ((Test-Path -LiteralPath $targetPath) -and -not $Force) {
        Write-Warning "Target already exists; skipping: $targetPath"
        $skipped++
        continue
    }

    $temporaryPath = Join-Path $sourceFile.DirectoryName (
        '.{0}.{1}.tmp' -f $sourceFile.BaseName, [System.Guid]::NewGuid().ToString('N')
    )
    $sourceArchive = $null
    $targetArchive = $null
    $sourceStream = $null
    $targetStream = $null

    try {
        $sourceStream = [System.IO.File]::Open(
            $sourceFile.FullName,
            [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read,
            [System.IO.FileShare]::Read
        )
        $sourceArchive = [System.IO.Compression.ZipArchive]::new(
            $sourceStream,
            [System.IO.Compression.ZipArchiveMode]::Read,
            $false
        )
        $sourceEntries = @($sourceArchive.Entries)

        $targetStream = [System.IO.File]::Open(
            $temporaryPath,
            [System.IO.FileMode]::CreateNew,
            [System.IO.FileAccess]::ReadWrite,
            [System.IO.FileShare]::None
        )
        $targetArchive = [System.IO.Compression.ZipArchive]::new(
            $targetStream,
            [System.IO.Compression.ZipArchiveMode]::Create,
            $true
        )

        foreach ($sourceEntry in $sourceEntries) {
            $targetEntry = $targetArchive.CreateEntry(
                $sourceEntry.FullName,
                [System.IO.Compression.CompressionLevel]::Optimal
            )
            if ($sourceEntry.LastWriteTime.Year -ge 1980) {
                $targetEntry.LastWriteTime = $sourceEntry.LastWriteTime
            }
            if (-not $sourceEntry.FullName.EndsWith('/')) {
                $entryInput = $sourceEntry.Open()
                $entryOutput = $targetEntry.Open()
                try {
                    $entryInput.CopyTo($entryOutput)
                }
                finally {
                    $entryOutput.Dispose()
                    $entryInput.Dispose()
                }
            }
        }

        $targetArchive.Dispose()
        $targetArchive = $null
        $targetStream.Dispose()
        $targetStream = $null
        $sourceArchive.Dispose()
        $sourceArchive = $null
        $sourceStream.Dispose()
        $sourceStream = $null

        $validationStream = [System.IO.File]::OpenRead($temporaryPath)
        $validationArchive = [System.IO.Compression.ZipArchive]::new(
            $validationStream,
            [System.IO.Compression.ZipArchiveMode]::Read,
            $false
        )
        try {
            $validatedEntries = @($validationArchive.Entries)
            if ($validatedEntries.Count -ne $sourceEntries.Count) {
                throw "Entry-count validation failed. source=$($sourceEntries.Count), target=$($validatedEntries.Count)"
            }
        }
        finally {
            $validationArchive.Dispose()
            $validationStream.Dispose()
        }

        Move-Item -LiteralPath $temporaryPath -Destination $targetPath -Force:$Force
        if ($RemoveSource) {
            Remove-Item -LiteralPath $sourceFile.FullName -Force
        }
        Write-Host "Converted: $($sourceFile.FullName) -> $targetPath"
        $converted++
    }
    catch {
        $failed++
        Write-Error "Conversion failed: $($sourceFile.FullName)`n$($_.Exception.Message)" -ErrorAction Continue
    }
    finally {
        if ($targetArchive) { $targetArchive.Dispose() }
        if ($targetStream) { $targetStream.Dispose() }
        if ($sourceArchive) { $sourceArchive.Dispose() }
        if ($sourceStream) { $sourceStream.Dispose() }
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

Write-Host "Done: converted=$converted, skipped=$skipped, failed=$failed, found=$($sourceFiles.Count)"
if ($failed -gt 0) {
    exit 1
}
