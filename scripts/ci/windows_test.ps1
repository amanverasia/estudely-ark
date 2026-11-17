# CI test of a (partial) built pack on Windows: launchers, library server, map assets, Verify, AI.
#   powershell -ExecutionPolicy Bypass -File scripts/ci/windows_test.ps1 -Pack PATH [-NoAI]
param([Parameter(Mandatory = $true)][string]$Pack, [switch]$NoAI)
$ErrorActionPreference = 'Continue'
Write-Host "windows_test.ps1 starting (PowerShell $($PSVersionTable.PSVersion))"
$Pack = (Resolve-Path $Pack).Path
$results = New-Object System.Collections.ArrayList
$failed = 0
function Check($name, [scriptblock]$test) {
  try { $ok = & $test } catch { $ok = $false; Write-Host "  exception: $_" }
  if ($ok) { Write-Host "PASS  $name" } else { Write-Host "FAIL  $name"; $script:failed++ }
  [void]$results.Add(("{0}`t{1}" -f ($(if ($ok) { 'PASS' } else { 'FAIL' })), $name))
}
function Get-Status($url, $headers = @{}) {
  try { (Invoke-WebRequest -Uri $url -UseBasicParsing -Headers $headers -TimeoutSec 30).StatusCode } catch {
    if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 } }
}
function Wait-Url($url, $seconds) {
  $t = [DateTime]::Now.AddSeconds($seconds)
  while ([DateTime]::Now -lt $t) { if ((Get-Status $url) -eq 200) { return $true }; Start-Sleep 2 }
  return $false
}
function Stop-All { Get-Process kiwix-serve, miniserve, llama-server -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue }

Write-Host "Windows: $([Environment]::OSVersion.VersionString)  arch: $env:PROCESSOR_ARCHITECTURE"
$env:NO_BROWSER = '1'

# 1. Verify (signature + hashes).
Check 'Verify.ps1 reports genuine' {
  & powershell -NoProfile -ExecutionPolicy Bypass -File "$Pack\Verify.ps1" | Tee-Object -Variable out | Out-Host
  ($LASTEXITCODE -eq 0) -and (($out -join "`n") -match 'Signature: GOOD')
}
Check 'Verify.bat runs (double-click route)' {
  $o = cmd /c "echo.| `"$Pack\Verify.bat`"" 2>&1
  ($o -join "`n") -match 'genuine and undamaged'
}
$victim = Get-ChildItem -LiteralPath "$Pack\Core" -Recurse -File -Filter *.pdf | Select-Object -First 1
if ($victim) {
  $bytes = [IO.File]::ReadAllBytes($victim.FullName)
  $orig = $bytes[1000]; $bytes[1000] = $orig -bxor 0xFF; [IO.File]::WriteAllBytes($victim.FullName, $bytes)
  Check 'Verify.ps1 detects a changed byte' {
    & powershell -NoProfile -ExecutionPolicy Bypass -File "$Pack\Verify.ps1" | Out-Host
    $LASTEXITCODE -eq 1
  }
  $bytes[1000] = $orig; [IO.File]::WriteAllBytes($victim.FullName, $bytes)
}

# 2. Library launcher.
$lib = Start-Process -FilePath cmd.exe -ArgumentList '/c', "`"$Pack\Start-Library.bat`"" -PassThru -WindowStyle Hidden
Check 'Start-Library.bat serves START-HERE' { Wait-Url 'http://127.0.0.1:8471/START-HERE.html' 60 }
Check 'kiwix-serve runs (ICU DLLs present)' { Wait-Url 'http://127.0.0.1:8470/' 30 }
Check 'ZIM opens by undated alias' { (Get-Status 'http://127.0.0.1:8470/content/zimgit-water_en/') -eq 200 }
Check 'Full-text search inside a ZIM' {
  $r = Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:8470/search?books.name=devdocs_en_python_2026-10&pattern=list'
  $r.Content -match 'results'
}
Check 'Hesperian PDF served' {
  $pdf = Get-ChildItem -LiteralPath "$Pack\Core\02-Health-and-Sanitation\Hesperian" -Recurse -File -Filter *.pdf | Select-Object -First 1
  $rel = $pdf.FullName.Substring($Pack.Length + 1) -replace '\\', '/'
  (Get-Status ("http://127.0.0.1:8471/" + [Uri]::EscapeUriString($rel))) -eq 200
}
Check 'Map page and packed fonts (HTTP Range 206)' {
  ((Get-Status 'http://127.0.0.1:8471/Core/05-Maps/Desktop-Map/index.html') -eq 200) -and
  # PowerShell 5.1 can't set a Range header via -Headers, so use Windows' built-in curl.exe.
  ((& curl.exe -s -o NUL -w '%{http_code}' -r 0-99 'http://127.0.0.1:8471/Core/05-Maps/Desktop-Map/fonts/glyphs.bin') -eq '206')
}
Check 'Map place search index served' { (Get-Status 'http://127.0.0.1:8471/Core/05-Maps/Desktop-Map/places.tsv') -eq 200 }
Check 'Library listens on 127.0.0.1 only by default' {
  $ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } | Select-Object -First 1).IPAddress
  Write-Host "  LAN address: $ip"
  (Get-Status "http://${ip}:8471/START-HERE.html") -ne 200
}
Stop-All; Stop-Process -Id $lib.Id -Force -ErrorAction SilentlyContinue; Start-Sleep 2

$share = Start-Process -FilePath cmd.exe -ArgumentList '/c', "`"$Pack\Start-Library.bat`" --share" -PassThru -WindowStyle Hidden
Check 'Start-Library.bat --share is reachable on the LAN address' {
  $ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } | Select-Object -First 1).IPAddress
  (Wait-Url "http://${ip}:8471/START-HERE.html" 60) -and (Wait-Url "http://${ip}:8470/" 30)
}
Stop-All; Stop-Process -Id $share.Id -Force -ErrorAction SilentlyContinue; Start-Sleep 2

# 3. AI launcher.
if (-not $NoAI -and (Test-Path "$Pack\AI\Gemma-4-E4B\gemma-4-E4B_q4_0-it.gguf")) {
  $env:AI_ARGS = '-t 4'
  # cmd /c strips the outermost quotes when there are more than two, so wrap the whole command in one extra pair.
  $ai = Start-Process -FilePath cmd.exe -ArgumentList '/c', "`"`"$Pack\Start-AI.bat`" > `"$env:RUNNER_TEMP\ai.log`" 2>&1`"" -PassThru -WindowStyle Hidden
  Start-Sleep 20
  if (Test-Path "$env:RUNNER_TEMP\ai.log") { Get-Content "$env:RUNNER_TEMP\ai.log" -TotalCount 12 | ForEach-Object { "  ai.log: $_" } } else { Write-Host '  ai.log: (not created)' }
  $t0 = Get-Date
  Check 'Start-AI.bat: llama-server becomes healthy' { Wait-Url 'http://127.0.0.1:8472/health' 600 }
  Write-Host ("  load time: {0:N0} s" -f ((Get-Date) - $t0).TotalSeconds)
  Check 'AI answers a question (text)' {
    $body = @{ messages = @(@{ role = 'user'; content = 'What is the capital of India? Answer in one short sentence.' }); max_tokens = 40 } | ConvertTo-Json -Depth 4
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $r = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8472/v1/chat/completions' -ContentType 'application/json' -Body $body -TimeoutSec 900
    $txt = $r.choices[0].message.content
    Write-Host ("  answer ({0:N0} s): {1}" -f $sw.Elapsed.TotalSeconds, $txt)
    if ($r.timings) { Write-Host ("  prompt {0:N1} tok/s, generation {1:N1} tok/s" -f $r.timings.prompt_per_second, $r.timings.predicted_per_second) }
    $txt -match 'Delhi'
  }
  Stop-All; Stop-Process -Id $ai.Id -Force -ErrorAction SilentlyContinue
  if (Test-Path "$env:RUNNER_TEMP\ai.log") { Get-Content "$env:RUNNER_TEMP\ai.log" -Tail 15 | ForEach-Object { "  ai.log: $_" } }
}

Write-Host ''
$results | ForEach-Object { Write-Host $_ }
if ($env:GITHUB_STEP_SUMMARY) {
  "| Result | Test |`n|---|---|" | Out-File -Append -Encoding utf8 $env:GITHUB_STEP_SUMMARY
  $results | ForEach-Object { $p = $_ -split "`t"; "| $($p[0]) | $($p[1]) |" } | Out-File -Append -Encoding utf8 $env:GITHUB_STEP_SUMMARY
}
exit $(if ($failed -gt 0) { 1 } else { 0 })
