param(
  [string]$SourcePath = 'D:/project/personal_blog_vite',
  [string]$SnapshotPath = (Join-Path $PSScriptRoot '../frontend')
)

$ErrorActionPreference = 'Stop'

function Get-FrontendManifest([string]$RootPath) {
  $root = (Resolve-Path -LiteralPath $RootPath).Path.TrimEnd('\', '/')
  $manifest = @{}
  foreach ($folder in @('src', 'public')) {
    $directory = Join-Path $root $folder
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
      throw "缺少前端目录：$directory"
    }
    foreach ($file in Get-ChildItem -LiteralPath $directory -Recurse -File -Force) {
      $relativePath = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
      $manifest[$relativePath] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    }
  }
  foreach ($name in @('package.json', 'package-lock.json', 'vite.config.js', 'index.html')) {
    $manifest[$name] = (Get-FileHash -LiteralPath (Join-Path $root $name) -Algorithm SHA256).Hash
  }
  return $manifest
}

try {
  $source = Get-FrontendManifest $SourcePath
  $snapshot = Get-FrontendManifest $SnapshotPath
  $paths = @($source.Keys) + @($snapshot.Keys) | Sort-Object -Unique
  $different = @($paths | Where-Object {
    -not $source.ContainsKey($_) -or -not $snapshot.ContainsKey($_) -or $source[$_] -ne $snapshot[$_]
  })
  if ($different.Count -gt 0) {
    Write-Output '前端快照不一致：'
    $different | ForEach-Object { Write-Output "  $_" }
    exit 1
  }
  Write-Output "前端快照一致，共检查 $($paths.Count) 个文件。"
  exit 0
} catch {
  Write-Error "前端快照检查失败：$($_.Exception.Message)" -ErrorAction Continue
  exit 1
}
