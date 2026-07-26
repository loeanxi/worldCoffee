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
  throw 'docker command not found. Start Docker Desktop first.'
}

$names = @(Get-ContainerNames)
$missingServices = New-Object System.Collections.Generic.List[string]

foreach ($item in $expected) {
  if ($names -contains $item.Container) {
    $running = docker inspect -f '{{.State.Running}}' $item.Container 2>$null
    if ($running -ne 'true') {
      Write-Host "Starting existing container $($item.Container) ..."
      docker start $item.Container | Out-Null
    } else {
      Write-Host "Container $($item.Container) is already running."
    }
  } else {
    $missingServices.Add($item.Service)
  }
}

if ($missingServices.Count -gt 0) {
  if (-not (Test-Path $composeFile)) {
    throw "Docker Compose file not found: $composeFile"
  }
  Write-Host "Creating missing infra containers: $($missingServices -join ', ')"
  docker compose -f $composeFile up -d @($missingServices.ToArray())
}

if ($Wait) {
  foreach ($item in $expected) {
    Write-Host "Waiting for $($item.Container) port $($item.Port) ..."
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
      if (Test-PortOpen $item.Port) {
        $ready = $true
        break
      }
      Start-Sleep -Seconds 1
    }
    if (-not $ready) {
      throw "$($item.Container) port $($item.Port) is not ready."
    }
  }
}

Write-Host 'Infrastructure is ready.'
