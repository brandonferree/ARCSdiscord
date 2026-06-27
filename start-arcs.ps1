# =============================================================================
# start-arcs.ps1 — one-shot launcher for the Arcs Discord bot + web viewer.
#
# A Cloudflare "quick tunnel" gets a NEW random *.trycloudflare.com hostname
# every time it starts, so the viewer URL changes on each run. This script does
# the whole dance for you:
#   1. stops any running bot / sbt / tunnel,
#   2. starts a fresh quick tunnel to the local web port and grabs its URL,
#   3. writes RENDER_PORT + PUBLIC_BASE_URL into .env (so the bot posts the link),
#   4. starts the bot and waits for it to come up.
#
# Usage (from the repo root):   .\start-arcs.ps1
# Optional:                     .\start-arcs.ps1 -Port 8787
#
# Requires cloudflared (winget install Cloudflare.cloudflared) and that .env
# already has your DISCORD_TOKEN etc. .env is gitignored; this only rewrites the
# RENDER_PORT / PUBLIC_BASE_URL lines and preserves everything else.
# =============================================================================
param(
  [int]$Port = 8787
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

# Locate cloudflared (PATH may be stale right after install).
$cf = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
if (-not $cf) { $cf = "C:\Program Files (x86)\cloudflared\cloudflared.exe" }
if (-not (Test-Path $cf)) {
  throw "cloudflared not found. Install it with:  winget install Cloudflare.cloudflared"
}
if (-not (Test-Path (Join-Path $root ".env"))) {
  throw ".env not found in $root (need DISCORD_TOKEN etc.)."
}

Write-Host "[1/4] Stopping any running bot / sbt / tunnel..."
Get-Process java,sbt,cloudflared -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.Id -Force }
Start-Sleep -Milliseconds 800

Write-Host "[2/4] Starting Cloudflare quick tunnel -> http://localhost:$Port ..."
if (Test-Path tunnel.log)     { Clear-Content tunnel.log }
if (Test-Path tunnel.out.log) { Clear-Content tunnel.out.log }
Start-Process -FilePath $cf -ArgumentList "tunnel","--url","http://localhost:$Port","--no-autoupdate" `
  -RedirectStandardOutput "tunnel.out.log" -RedirectStandardError "tunnel.log" -WindowStyle Hidden
$deadline = (Get-Date).AddSeconds(45); $url = $null
while ((Get-Date) -lt $deadline -and -not $url) {
  Start-Sleep -Seconds 2
  $txt = (Get-Content tunnel.log,tunnel.out.log -ErrorAction SilentlyContinue) -join "`n"
  $m = [regex]::Match($txt, 'https://[a-z0-9-]+\.trycloudflare\.com')
  if ($m.Success) { $url = $m.Value }
}
if (-not $url) { throw "Tunnel URL not found within 45s. See tunnel.log." }
Write-Host "      Tunnel: $url"

Write-Host "[3/4] Updating .env (RENDER_PORT, PUBLIC_BASE_URL)..."
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
$lines = Set-EnvLine $lines "PUBLIC_BASE_URL" $url
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
  Write-Host "Bot is UP. Public board base:  $url/game/<id>"
  Write-Host "In Discord, run /arcs link to post the URL for your table."
} else {
  Write-Host "Bot did not report 'up' within 180s. Check bot.log / bot.err.log."
}
