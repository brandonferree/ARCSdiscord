# =============================================================================
# start-arcs-named.ps1 — launcher for the Arcs bot + web viewer on the STABLE
# Cloudflare *named* tunnel (https://arcs.playarcs.cc), as opposed to the random
# quick-tunnel URL produced by start-arcs.ps1.
#
# A named tunnel keeps the SAME hostname across restarts, so:
#   * PUBLIC_BASE_URL (the shared board link) is permanent, and
#   * OAUTH_BASE / the OAuth redirect_uri (arcs.playarcs.cc/auth/callback) is
#     permanent too — no more re-registering the redirect in Discord each run.
#
# This script:
#   1. stops any running bot / sbt / tunnel,
#   2. starts the named tunnel `arcs` (routing comes from ~/.cloudflared/config.yml),
#   3. pins RENDER_PORT + PUBLIC_BASE_URL + OAUTH_BASE in .env,
#   4. starts the bot and waits for it to come up.
#
# Usage (from the repo root):   .\start-arcs-named.ps1
# Optional:                     .\start-arcs-named.ps1 -Port 8787
#
# One-time setup already done in this repo's session:
#   cloudflared tunnel login
#   cloudflared tunnel create arcs
#   cloudflared tunnel route dns arcs arcs.playarcs.cc
#   ~/.cloudflared/config.yml maps arcs.playarcs.cc -> http://localhost:8787
#
# Still required ONCE in the Discord Developer Portal (OAuth2 -> Redirects):
#   https://arcs.playarcs.cc/auth/callback
# and DISCORD_CLIENT_ID / DISCORD_CLIENT_SECRET present in .env.
# =============================================================================
param(
  [int]$Port = 8787
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$HostName  = "arcs.playarcs.cc"
$BaseUrl   = "https://$HostName"

# Locate cloudflared (PATH may be stale right after install).
$cf = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
if (-not $cf) { $cf = "C:\Program Files (x86)\cloudflared\cloudflared.exe" }
if (-not (Test-Path $cf)) {
  throw "cloudflared not found. Install it with:  winget install Cloudflare.cloudflared"
}
if (-not (Test-Path (Join-Path $root ".env"))) {
  throw ".env not found in $root (need DISCORD_TOKEN etc.)."
}
$cfgPath = Join-Path $env:USERPROFILE ".cloudflared\config.yml"
if (-not (Test-Path $cfgPath)) {
  throw "Named-tunnel config not found at $cfgPath. Run the one-time setup (see header)."
}

Write-Host "[1/4] Stopping any running bot / sbt / tunnel..."
Get-Process java,sbt,cloudflared -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.Id -Force }
Start-Sleep -Milliseconds 800

Write-Host "[2/4] Starting Cloudflare NAMED tunnel 'arcs' -> $BaseUrl ..."
if (Test-Path tunnel.log)     { Clear-Content tunnel.log }
if (Test-Path tunnel.out.log) { Clear-Content tunnel.out.log }
# `tunnel run arcs` reads ~/.cloudflared/config.yml for ingress + credentials.
Start-Process -FilePath $cf -ArgumentList "tunnel","--no-autoupdate","run","arcs" `
  -RedirectStandardOutput "tunnel.out.log" -RedirectStandardError "tunnel.log" -WindowStyle Hidden
# Wait for the tunnel to register at least one connection before we rely on it.
$deadline = (Get-Date).AddSeconds(45); $ready = $false
while ((Get-Date) -lt $deadline -and -not $ready) {
  Start-Sleep -Seconds 2
  $txt = (Get-Content tunnel.log,tunnel.out.log -ErrorAction SilentlyContinue) -join "`n"
  if ($txt -match "Registered tunnel connection" -or $txt -match "Connection .* registered") { $ready = $true }
}
if (-not $ready) { throw "Named tunnel did not register a connection within 45s. See tunnel.log." }
Write-Host "      Tunnel up: $BaseUrl"

Write-Host "[3/4] Updating .env (RENDER_PORT, PUBLIC_BASE_URL, OAUTH_BASE)..."
$envPath = Join-Path $root ".env"
$lines = @(Get-Content $envPath)
function Set-EnvLine([string[]]$lines, [string]$key, [string]$val) {
  $found = $false
  $out = foreach ($l in $lines) {
    if ($l -match "^\s*$([regex]::Escape($key))=") { $found = $true; "$key=$val" } else { $l }
  }
  if (-not $found) { $out = @($out) + "$key=$val" }
  ,@($out)
}
$lines = Set-EnvLine $lines "RENDER_PORT" "$Port"
$lines = Set-EnvLine $lines "PUBLIC_BASE_URL" $BaseUrl
$lines = Set-EnvLine $lines "OAUTH_BASE" $BaseUrl
# Write UTF-8 *without* BOM — a BOM would corrupt the first key (DISCORD_TOKEN)
# when the launcher parses .env line-by-line.
[System.IO.File]::WriteAllLines($envPath, $lines, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "[4/4] Starting the bot..."
Get-Content $envPath | ForEach-Object {
  if ($_ -match '^\s*([^#=]+)=(.*)$') { Set-Item -Path "Env:$($matches[1].Trim())" -Value $matches[2].Trim() }
}
if (Test-Path bot.log)     { Clear-Content bot.log }
if (Test-Path bot.err.log) { Clear-Content bot.err.log }
Start-Process -FilePath "sbt" -ArgumentList "bot/run" `
  -RedirectStandardOutput "bot.log" -RedirectStandardError "bot.err.log" -WindowStyle Hidden

$deadline = (Get-Date).AddSeconds(180); $up = $false
while ((Get-Date) -lt $deadline) {
  if ((Test-Path bot.log) -and ((Get-Content bot.log -Raw) -match "arcs-discord bot is up")) { $up = $true; break }
  Start-Sleep -Seconds 3
}
Write-Host ""
if ($up) {
  Write-Host "Bot is UP. Permanent board base:  $BaseUrl/game/<id>"
  Write-Host "OAuth redirect (register once in Discord): $BaseUrl/auth/callback"
  Write-Host "In Discord, run /arcs link to post the URL for your table."
} else {
  Write-Host "Bot did not report 'up' within 180s. Check bot.log / bot.err.log."
}
