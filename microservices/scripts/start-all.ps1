param(
  [switch]$SkipInfra,
  [switch]$SkipBuild,
  [switch]$SkipSql,
  [switch]$SkipFrontend,
  [switch]$SkipHealthCheck
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$microRoot = Join-Path $repoRoot 'microservices'
$runRoot = Join-Path $microRoot '.run'
$logRoot = Join-Path $runRoot 'logs'
$pidRoot = Join-Path $runRoot 'pids'

New-Item -ItemType Directory -Force -Path $logRoot, $pidRoot | Out-Null

function Test-CommandExists($name) {
  return [bool](Get-Command $name -ErrorAction SilentlyContinue)
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

function Start-JavaService($name, [int]$port, $jarRelative) {
  $jar = Join-Path $microRoot $jarRelative
  if (Test-PortOpen $port) {
    Write-Host "$name 端口 $port 已监听，跳过启动。"
    return
  }
  if (-not (Test-Path $jar)) {
    throw "$name jar 不存在：$jar。请先执行脚本时不要加 -SkipBuild。"
  }
  $out = Join-Path $logRoot "$name.out.log"
  $err = Join-Path $logRoot "$name.err.log"
  Write-Host "启动 $name -> port $port"
  $proc = Start-Process -FilePath 'java' `
    -ArgumentList @("-DLOG_DIR=$logRoot", '-jar', $jar) `
    -WorkingDirectory $microRoot `
    -RedirectStandardOutput $out `
    -RedirectStandardError $err `
    -PassThru `
    -WindowStyle Hidden
  Set-Content -Path (Join-Path $pidRoot "$name.pid") -Value $proc.Id -Encoding ASCII
}

function Start-Frontend($name, $dirRelative, [int]$port) {
  if (Test-PortOpen $port) {
    Write-Host "$name 端口 $port 已监听，跳过启动。"
    return
  }
  $dir = Join-Path $repoRoot $dirRelative
  if (-not (Test-Path $dir)) { throw "$name 目录不存在：$dir" }
  if (-not (Test-Path (Join-Path $dir 'node_modules'))) {
    Write-Host "$name 未发现 node_modules，执行 npm install ..."
    Push-Location $dir
    try { npm install } finally { Pop-Location }
  }
  $out = Join-Path $logRoot "$name.out.log"
  $err = Join-Path $logRoot "$name.err.log"
  Write-Host "启动 $name -> port $port"
  $proc = Start-Process -FilePath 'npm.cmd' `
    -ArgumentList @('run', 'dev', '--', '--host', '127.0.0.1', '--port', "$port") `
    -WorkingDirectory $dir `
    -RedirectStandardOutput $out `
    -RedirectStandardError $err `
    -PassThru `
    -WindowStyle Hidden
  Set-Content -Path (Join-Path $pidRoot "$name.pid") -Value $proc.Id -Encoding ASCII
}

function Invoke-SqlFile($file) {
  if (-not (Test-Path $file)) { return }
  Write-Host "执行 SQL：$file"
  $cmd = "type `"$file`" | docker exec -i mysql8 mysql --default-character-set=utf8mb4 -uroot -p123456 worldCoffee"
  cmd /c $cmd
}

if (-not (Test-CommandExists java)) { throw '未找到 java，请安装 Java 21 并加入 PATH。' }
if (-not (Test-CommandExists mvn)) { throw '未找到 mvn，请安装 Maven 并加入 PATH。' }

if (-not $SkipInfra) {
  & (Join-Path $PSScriptRoot 'start-infra.ps1') -Wait
}

if (-not $SkipBuild) {
  Push-Location $microRoot
  try {
    Write-Host '后端打包：mvn -DskipTests package'
    mvn -DskipTests package
  } finally {
    Pop-Location
  }
}

if (-not $SkipSql) {
  Invoke-SqlFile (Join-Path $microRoot 'wc-community\src\main\resources\db\feed_event.sql')
  Invoke-SqlFile (Join-Path $microRoot 'wc-community\src\main\resources\db\community_phase1.sql')
  Invoke-SqlFile (Join-Path $microRoot 'wc-community\src\main\resources\db\community_phase2.sql')
  Invoke-SqlFile (Join-Path $microRoot 'wc-admin\src\main\resources\admin_governance.sql')
}

$javaServices = @(
  @{ Name = 'wc-user'; Port = 8082; Jar = 'wc-user\target\wc-user-0.0.1-SNAPSHOT-exec.jar' },
  @{ Name = 'wc-shop'; Port = 8081; Jar = 'wc-shop\target\wc-shop-0.0.1-SNAPSHOT-exec.jar' },
  @{ Name = 'wc-community'; Port = 8083; Jar = 'wc-community\target\wc-community-0.0.1-SNAPSHOT-exec.jar' },
  @{ Name = 'wc-message'; Port = 8084; Jar = 'wc-message\target\wc-message-0.0.1-SNAPSHOT.jar' },
  @{ Name = 'wc-ai'; Port = 8085; Jar = 'wc-ai\target\wc-ai-0.0.1-SNAPSHOT.jar' },
  @{ Name = 'wc-admin'; Port = 8086; Jar = 'wc-admin\target\wc-admin-0.0.1-SNAPSHOT.jar' },
  @{ Name = 'wc-gateway'; Port = 8080; Jar = 'wc-gateway\target\wc-gateway-0.0.1-SNAPSHOT.jar' }
)

foreach ($svc in $javaServices) {
  Start-JavaService $svc.Name $svc.Port $svc.Jar
}

if (-not $SkipFrontend) {
  Start-Frontend 'frontend' 'frontend' 3000
  Start-Frontend 'admin-frontend' 'admin-frontend' 5173
}

if (-not $SkipHealthCheck) {
  Write-Host '等待服务注册与端口稳定 ...'
  Start-Sleep -Seconds 12
  & (Join-Path $PSScriptRoot 'health-check.ps1')
}

Write-Host ''
Write-Host '启动完成。'
Write-Host '用户端：http://localhost:3000'
Write-Host '管理端：http://localhost:5173'
Write-Host '网关：http://localhost:8080'
Write-Host "日志目录：$logRoot"
