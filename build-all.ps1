# 依次构建两个平台的 jar。
#   .\build-all.ps1
# 注意：forge-1.20.1 首次运行需要联网下载 Gradle 8.8 与 JDK 17（foojay 自动解析）。
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$targets = @('neoforge-1.21.1', 'forge-1.20.1')

foreach ($target in $targets) {
    Write-Host "`n=== Building $target ===" -ForegroundColor Cyan
    Push-Location (Join-Path $root $target)
    try {
        & .\gradlew.bat build --console=plain
        if ($LASTEXITCODE -ne 0) {
            Write-Host "=== $target FAILED (exit $LASTEXITCODE) ===" -ForegroundColor Red
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
    Write-Host "=== $target OK ===" -ForegroundColor Green
}

Write-Host "`nAll builds finished. Jars are in <target>/build/libs/." -ForegroundColor Green
