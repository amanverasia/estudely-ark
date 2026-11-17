# Estudely Ark: check that this copy is genuine and undamaged (Windows 10 and 11).
# Run it by double-clicking Verify.bat.
#
# 1. Checks MANIFEST.tsv was signed by the Estudely Ark project key (uses ssh-keygen, part of Windows' OpenSSH client).
# 2. Checks every file you have against the size and SHA-256 fingerprint in MANIFEST.tsv.
#    Modules you chose not to download are listed as "not downloaded", which is fine.
#
# IMPORTANT: compare the key fingerprint printed below with the one published OUTSIDE this pack
# (project page, the message you got the pack from, or the printed card). A forged pack can include a forged key.

$ErrorActionPreference = 'Continue'
Set-Location -LiteralPath $PSScriptRoot
$NS = 'estudely-ark'
$SIGNER = 'estudely-ark-release'
$fail = $false

foreach ($f in 'MANIFEST.tsv', 'MANIFEST.tsv.sig', 'KEYS\allowed_signers') {
  if (-not (Test-Path -LiteralPath $f)) {
    Write-Host "$f is missing. Download the top-level files too."
    exit 2
  }
}

$ssh = Get-Command ssh-keygen.exe -ErrorAction SilentlyContinue
if (-not $ssh -and (Test-Path "$env:WINDIR\System32\OpenSSH\ssh-keygen.exe")) { $ssh = Get-Item "$env:WINDIR\System32\OpenSSH\ssh-keygen.exe" }

Write-Host 'Project key fingerprint (compare with the published one):'
if ($ssh) {
  $parts = (Get-Content -LiteralPath 'KEYS\allowed_signers' -TotalCount 1) -split ' '
  $tmp = [IO.Path]::GetTempFileName()
  Set-Content -LiteralPath $tmp -Value ($parts[2] + ' ' + $parts[3]) -Encoding ascii
  & $ssh.Source -lf $tmp | ForEach-Object { "  $_" }
  Remove-Item -LiteralPath $tmp
}
Write-Host ''

if ($ssh) {
  cmd /c "`"$($ssh.Source)`" -Y verify -f KEYS\allowed_signers -I $SIGNER -n $NS -s MANIFEST.tsv.sig < MANIFEST.tsv" *> $null
  if ($LASTEXITCODE -eq 0) {
    Write-Host 'Signature: GOOD (MANIFEST.tsv was signed by the project key)'
  } else {
    Write-Host 'Signature: BAD. Do not trust this copy. MANIFEST.tsv was changed or not signed by the project key.' -ForegroundColor Red
    $fail = $true
  }
} else {
  Write-Host 'Signature: could not check (ssh-keygen not found; install "OpenSSH Client" under Settings > Optional features). File checks below still run.'
}
Write-Host ''
Write-Host 'Checking files. Large files take a while...'

$ok = 0; $bad = 0; $missing = 0
foreach ($line in Get-Content -LiteralPath 'MANIFEST.tsv' -Encoding UTF8) {
  if ($line -eq '' -or $line.StartsWith('#')) { continue }
  $c = $line -split "`t"
  $path = $c[0] -replace '/', '\'
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { $missing++; continue }
  $got = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLower()
  if ($got -eq $c[2]) { $ok++ } else { $bad++; Write-Host "DAMAGED OR CHANGED: $($c[0])" -ForegroundColor Red }
}

Write-Host "Files OK: $ok   Damaged/changed: $bad   Not downloaded: $missing"
if ($bad -gt 0) { $fail = $true }
if (-not $fail) {
  Write-Host 'RESULT: this copy is genuine and undamaged.' -ForegroundColor Green
  exit 0
} else {
  Write-Host 'RESULT: PROBLEM FOUND. Re-download the files listed above, or get the pack from another source.' -ForegroundColor Red
  exit 1
}
