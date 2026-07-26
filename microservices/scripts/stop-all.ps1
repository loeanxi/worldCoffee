param(
  [switch]$StopInfra
)

$ErrorActionPreference = 'Continue'
$microRoot = Split-Path -Parent $PSScriptRoot
$pidRoot = Join-Path $microRoot '.run\pids'

function Test-PortOpen([int]$port) {
  try {
    $client = New-Object System.Net.Sockets.TcpClient
    $async = $client.BeginConnect('127.0.0.1', $port, $null, $null)
    $ok = $async.AsyncWaitHandle.WaitOne(300)
    if ($ok) { $client.EndConnect($async) }
    $client.Close()
    return $ok
  } catch {
    return $false
  }
}

function Get-PortPid([int]$port) {
  $lines = netstat -ano | Select-String -Pattern "LISTENING\s+(\d+)$" | Where-Object { $_.Line -match "[:.]$port\s" }
  foreach ($line in $lines) {
    if ($line.Line -match 'LISTENING\s+(\d+)$') {
      return [int]$Matches[1]
    }
  }
  return $null
}

function Stop-PortOwner([int]$port, [string]$name) {
  $pidOnPort = Get-PortPid $port
  if (-not $pidOnPort) { return }

  $proc = Get-Process -Id $pidOnPort -ErrorAction SilentlyContinue
  $processName = if ($proc) { $proc.ProcessName } else { 'unknown' }
  Write-Host "Stopping $name on port $port pid=$pidOnPort process=$processName"
  Stop-Process -Id $pidOnPort -Force -ErrorAction SilentlyContinue

  for ($i = 0; $i -lt 20; $i++) {
    if (-not (Test-PortOpen $port)) { return }
    Start-Sleep -Milliseconds 300
  }

  Write-Host "Warning: port $port is still listening after stopping pid=$pidOnPort"
}

if (Test-Path $pidRoot) {
  Get-ChildItem $pidRoot -Filter '*.pid' | ForEach-Object {
    $name = $_.BaseName
    $pidText = Get-Content $_.FullName -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($pidText -match '^\d+$') {
      $proc = Get-Process -Id ([int]$pidText) -ErrorAction SilentlyContinue
      if ($proc) {
        Write-Host "Stopping $name pid=$pidText"
        Stop-Process -Id ([int]$pidText) -Force
      } else {
        Write-Host "$name pid=$pidText is not running."
      }
    }
  }
} else {
  Write-Host 'No .run\pids directory found. Services may not have been started by start-all.ps1.'
}

Write-Host 'Checking WorldCoffee ports ...'
$knownPorts = @(
  @{ Name = 'frontend'; Port = 3000 },
  @{ Name = 'admin-frontend'; Port = 5173 },
  @{ Name = 'wc-gateway'; Port = 8080 },
  @{ Name = 'wc-shop'; Port = 8081 },
  @{ Name = 'wc-user'; Port = 8082 },
  @{ Name = 'wc-community'; Port = 8083 },
  @{ Name = 'wc-message'; Port = 8084 },
  @{ Name = 'wc-ai'; Port = 8085 },
  @{ Name = 'wc-admin'; Port = 8086 }
)

foreach ($item in $knownPorts) {
  if (Test-PortOpen $item.Port) {
    Stop-PortOwner $item.Port $item.Name
  }
}

if ($StopInfra) {
  Write-Host 'Stopping infra containers ...'
  foreach ($container in @('chroma','worldcoffee-minio','es','nacos','rabbitmq','redis','mysql8')) {
    $exists = docker ps -a --format '{{.Names}}' | Where-Object { $_ -eq $container }
    if ($exists) {
      docker stop $container | Out-Null
      Write-Host "Stopped $container"
    }
  }
}

Write-Host 'Stop finished.'
