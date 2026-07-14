param(
  [switch]$StopInfra
)

$ErrorActionPreference = 'Continue'
$microRoot = Split-Path -Parent $PSScriptRoot
$pidRoot = Join-Path $microRoot '.run\pids'

if (Test-Path $pidRoot) {
  Get-ChildItem $pidRoot -Filter '*.pid' | ForEach-Object {
    $name = $_.BaseName
    $pidText = Get-Content $_.FullName -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($pidText -match '^\d+$') {
      $proc = Get-Process -Id ([int]$pidText) -ErrorAction SilentlyContinue
      if ($proc) {
        Write-Host "停止 $name pid=$pidText"
        Stop-Process -Id ([int]$pidText) -Force
      } else {
        Write-Host "$name pid=$pidText 已不存在"
      }
    }
  }
} else {
  Write-Host '未找到 .run\pids，可能不是通过 start-all.ps1 启动。'
}

if ($StopInfra) {
  Write-Host '停止基础设施容器 ...'
  foreach ($container in @('chroma','worldcoffee-minio','es','nacos','rabbitmq','redis','mysql8')) {
    $exists = docker ps -a --format '{{.Names}}' | Where-Object { $_ -eq $container }
    if ($exists) {
      docker stop $container | Out-Null
      Write-Host "已停止 $container"
    }
  }
}

Write-Host '停止完成。'
