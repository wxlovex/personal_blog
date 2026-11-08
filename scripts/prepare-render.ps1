param([string]$FrontendPath = (Join-Path $PSScriptRoot '../../personal_blog_vite'))

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourceRoot = (Resolve-Path -LiteralPath $FrontendPath).Path
$destinationRoot = Join-Path $repoRoot 'frontend'
if ($sourceRoot -eq $destinationRoot) { throw 'Source and destination must differ.' }
foreach ($name in @('package.json', 'package-lock.json', 'vite.config.js', 'index.html', 'src', 'public')) {
    if (-not (Test-Path -LiteralPath (Join-Path $sourceRoot $name))) { throw "Missing frontend input: $name" }
}
New-Item -ItemType Directory -Force -Path $destinationRoot | Out-Null
if ((Get-Item -LiteralPath $destinationRoot).Attributes -band [IO.FileAttributes]::ReparsePoint) {
    throw 'Frontend destination cannot be a symlink or junction.'
}
foreach ($name in @('src', 'public')) {
    $targetPath = [IO.Path]::GetFullPath((Join-Path $destinationRoot $name))
    if (-not $targetPath.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar)) {
        throw "Refusing to modify path outside repository: $targetPath"
    }
    if (Test-Path -LiteralPath $targetPath) {
        if ((Get-Item -LiteralPath $targetPath).Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "Refusing to replace linked directory: $targetPath"
        }
        Remove-Item -LiteralPath $targetPath -Recurse -Force
    }
    Copy-Item -LiteralPath (Join-Path $sourceRoot $name) -Destination $destinationRoot -Recurse
}
foreach ($name in @('package.json', 'package-lock.json', 'vite.config.js', 'index.html')) {
    Copy-Item -LiteralPath (Join-Path $sourceRoot $name) -Destination $destinationRoot -Force
}
Write-Output "Render frontend snapshot updated: $destinationRoot"
