# 依次构建两个平台的 jar。
#   .\build-all.ps1
#
# 首次运行需要联网：fabric-1.21.1 要 Gradle 9.8.1，且**运行 Gradle 的 JVM 必须是 JDK 25**
# （Loom 1.18 的 class 文件版本）；编译目标仍是 toolchain 的 JDK 21，由 foojay 自动解析。
# 找不到 JDK 25 时会明确报错，而不是给出费解的 "Dependency requires at least JVM
# runtime version 25"。
#
# 原生命令（gradlew.bat）往 stderr 写日志是常态——Loom 的提示、JVM 警告之类。
# PS7 在 $ErrorActionPreference='Stop' 下会把这些 stderr 行当成终止性错误，于是脚本会在
# 构建其实成功时中断（踩过）。改成 Continue，失败与否只看 $LASTEXITCODE。
$ErrorActionPreference = 'Continue'
$PSNativeCommandUseErrorActionPreference = $false
$root = $PSScriptRoot
$targets = @('neoforge-1.21.1', 'fabric-1.21.1')

function Find-Jdk25 {
    if ($env:KEYBINDLAYERS_JDK25) { return $env:KEYBINDLAYERS_JDK25 }
    $found = Get-ChildItem 'C:\Program Files\Java' -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($found) { return $found.FullName }
    return $null
}

$fabricJdk = Find-Jdk25
if (-not $fabricJdk) {
    throw 'Fabric 工程需要 JDK 25 来运行 Gradle（Loom 1.18 的要求）。请安装 JDK 25，或用 KEYBINDLAYERS_JDK25 环境变量指出它的路径。'
}
Write-Host "Fabric build will run Gradle on: $fabricJdk" -ForegroundColor DarkGray

foreach ($target in $targets) {
    Write-Host "`n=== Building $target ===" -ForegroundColor Cyan
    Push-Location (Join-Path $root $target)
    try {
        if ($target -like 'fabric-*') {
            $env:JAVA_HOME = $fabricJdk
        }
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
