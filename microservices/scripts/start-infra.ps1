param(
  [switch]$Wait
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $root 'docker-compose.yml'

$expected = @(
  @{ Container = 'mysql8'; Service = 'mysql8'; Port = 3306 },
  @{ Container = 'redis'; Service = 'redis'; Port = 6379 },
  @{ Container = 'rabbitmq'; Service = 'rabbitmq'; Port = 5672 },
  @{ Container = 'nacos'; Service = 'nacos'; Port = 8848 },
  @{ Container = 'es'; Service = 'es'; Port = 9200 },
  @{ Container = 'worldcoffee-minio'; Service = 'worldcoffee-minio'; Port = 9000 },
  @{ Container = 'chroma'; Service = 'chroma'; Port = 8000 }
)

function Test-CommandExists($name) {
  return [bool](Get-Command $name -ErrorAction SilentlyContinue)
}

function Get-ContainerNames {
  docker ps -a --format '{{.Names}}'
}

function Test-PortOpen([int]$port) {
  try {
    $client = [Net.Sockets.TcpClient]::new()
    $iar = $client.BeginConnect('127.0.0.1', $port, $null, $null)
    $ok = $iar.AsyncWaitHandle.WaitOne(800)
    if ($ok) { $client.EndConnect($iar) }
    $client.Close()
    return $ok
  } catch {
    return $false
  }
}

if (-not (Test-CommandExists docker)) {
  throw '未找到 docker 命令，请先启动 Docker Desktop。'
}

$names = @(Get-ContainerNames)
$missingServices = New-Object System.Collections.Generic.List[string]

foreach ($item in $expected) {
  if ($names -contains $item.Container) {
    $running = docker inspect -f '{{.State.Running}}' $item.Container 2>$null
    if ($running -ne 'true') {
      Write-Host "启动已有容器 $($item.Container) ..."
      docker start $item.Container | Out-Null
    } else {
      Write-Host "容器 $($item.Container) 已运行"
    }
  } else {
    $missingServices.Add($item.Service)
  }
}

if ($missingServices.Count -gt 0) {
  if (-not (Test-Path $composeFile)) {
    throw "缺少 Docker Compose 文件：$composeFile"
  }
  Write-Host "创建缺失的基础设施容器：$($missingServices -join ', ')"
  docker compose -f $composeFile up -d @($missingServices.ToArray())
}

if ($Wait) {
  foreach ($item in $expected) {
    Write-Host "等待 $($item.Container) 端口 $($item.Port) ..."
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
      if (Test-PortOpen $item.Port) {
        $ready = $true
        break
      }
      Start-Sleep -Seconds 1
    }
    if (-not $ready) {
      throw "$($item.Container) 端口 $($item.Port) 未就绪"
    }
  }
}

Write-Host '基础设施已启动。'
