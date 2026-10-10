# 从 Modrinth 拉取指定 mod 的 1.21.1 / NeoForge 版本，提取其语言文件里的按键分类与按键名。
#
#   .\tools\probe-mod-keybinds.ps1 -Slugs create,ae2 -OutDir "$env:TEMP\kbmods"
#
# 用途：给「知名 mod 的默认层级适配」提供真实键名依据——分类字符串来自 mod 自己的
# assets/<modid>/lang/en_us.json，不靠猜。jar 只下载到临时目录用于读取，不装进游戏。

param(
    [Parameter(Mandatory = $true)][string[]]$Slugs,
    [string]$OutDir = "$env:TEMP\kbmods",
    [string]$GameVersion = '1.21.1'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$loaderFilter = [uri]::EscapeDataString('["neoforge"]')
$versionFilter = [uri]::EscapeDataString('["' + $GameVersion + '"]')

foreach ($slug in $Slugs) {
    Write-Host "`n=== $slug ===" -ForegroundColor Cyan
    try {
        $api = "https://api.modrinth.com/v2/project/$slug/version?loaders=$loaderFilter&game_versions=$versionFilter"
        $versions = Invoke-RestMethod -Uri $api -TimeoutSec 40
    } catch {
        Write-Host "  query failed: $($_.Exception.Message)" -ForegroundColor Red
        continue
    }
    if (-not $versions -or $versions.Count -eq 0) {
        Write-Host "  no $GameVersion neoforge build" -ForegroundColor Yellow
        continue
    }

    $version = $versions[0]
    $file = $version.files | Where-Object { $_.primary } | Select-Object -First 1
    if (-not $file) { $file = $version.files[0] }
    $jarPath = Join-Path $OutDir "$slug.jar"

    if (-not (Test-Path $jarPath)) {
        try {
            Invoke-WebRequest -Uri $file.url -OutFile $jarPath -TimeoutSec 600
        } catch {
            Write-Host "  download failed: $($_.Exception.Message)" -ForegroundColor Red
            continue
        }
    }
    $sizeKb = [math]::Round((Get-Item $jarPath).Length / 1KB)
    Write-Host "  version=$($version.version_number)  file=$($file.filename)  size=${sizeKb}KB"

    $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $langEntries = $zip.Entries | Where-Object { $_.FullName -match '^assets/[^/]+/lang/en_us\.json$' }
        if (-not $langEntries) {
            Write-Host "  no assets/*/lang/en_us.json" -ForegroundColor Yellow
        }
        foreach ($entry in $langEntries) {
            $reader = New-Object System.IO.StreamReader($entry.Open())
            $text = $reader.ReadToEnd()
            $reader.Dispose()
            try {
                $json = $text | ConvertFrom-Json
            } catch {
                Write-Host "  [$($entry.FullName)] parse failed" -ForegroundColor Yellow
                continue
            }
            $categories = @()
            $keys = @()
            foreach ($prop in $json.PSObject.Properties) {
                if ($prop.Name -like 'key.categories.*') { $categories += $prop.Name }
                elseif ($prop.Name -like 'key.*') { $keys += $prop.Name }
            }
            Write-Host "  [$($entry.FullName)]"
            Write-Host "    categories ($($categories.Count)): $($categories -join ', ')" -ForegroundColor Green
            Write-Host "    keys ($($keys.Count)): $($keys -join ', ')"
        }
    } finally {
        $zip.Dispose()
    }
}
