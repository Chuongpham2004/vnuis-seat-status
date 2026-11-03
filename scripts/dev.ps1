<#
.SYNOPSIS
  Windows helper for the local stack (same targets as the Makefile).
.EXAMPLE
  ./scripts/dev.ps1 up
  ./scripts/dev.ps1 migrate
  ./scripts/dev.ps1 reset
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'down', 'migrate', 'reset', 'ps', 'logs', 'psql', 'redis', 'mqtt-sub', 'app')]
    [string]$Command = 'up'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $root 'infra/docker-compose.yml'

function Invoke-Compose {
    docker compose -f $composeFile @args
    if ($LASTEXITCODE -ne 0) { throw "docker compose $args failed ($LASTEXITCODE)" }
}

switch ($Command) {
    'up'      { Invoke-Compose up -d --wait }
    'down'    { Invoke-Compose down }
    'migrate' { Invoke-Compose --profile migrate run --rm flyway migrate }
    'reset'   {
        Invoke-Compose down -v
        Invoke-Compose up -d --wait
        Invoke-Compose --profile migrate run --rm flyway migrate
    }
    'ps'      { Invoke-Compose ps }
    'logs'    { Invoke-Compose logs -f --tail=100 }
    'psql'    { docker exec -it lib-postgres psql -U app -d library }
    'redis'   { docker exec -it lib-redis redis-cli }
    'mqtt-sub' {
        docker run --rm -it --network smart-library_default eclipse-mosquitto:2 `
            mosquitto_sub -h emqx -u monitor -P monitor_dev_pw -t 'library/#' -v
    }
    'app'     { Invoke-Compose --profile app up -d --build }
}
