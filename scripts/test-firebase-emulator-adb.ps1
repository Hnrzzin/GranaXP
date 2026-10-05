$ErrorActionPreference = 'Stop'
$cleanupScript = Join-Path $PSScriptRoot 'firebase-emulator-adb.ps1'
$testState = @{
    Forwards = @{}
    Removed = [System.Collections.Generic.List[string]]::new()
    FailPort = $null
    KeepPort = $null
}
$savedExitCode = $global:LASTEXITCODE

# A controlled ADB command lets this regression run without a device or Firebase.
function Invoke-TestAdb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $global:LASTEXITCODE = 0
    if ($Arguments[2] -eq 'get-state') { return 'device' }
    if ($Arguments[2] -ne 'reverse') { throw 'Unexpected ADB command in test.' }
    switch ($Arguments[3]) {
        '--list' {
            foreach ($entry in $testState.Forwards.GetEnumerator()) {
                "test $($entry.Key) $($entry.Value)"
            }
        }
        '--remove' {
            $port = $Arguments[4]
            $testState.Removed.Add($port)
            if ($port -eq $testState.FailPort -or -not $testState.Forwards.ContainsKey($port)) {
                $global:LASTEXITCODE = 1
                return
            }
            if ($port -ne $testState.KeepPort) { $testState.Forwards.Remove($port) }
        }
        '--remove-all' { $testState.Forwards.Clear() }
        default { throw 'Unexpected ADB reverse command in test.' }
    }
}

function Assert-Cleaned {
    foreach ($port in @(9099, 8080, 5001)) {
        if ($testState.Forwards.ContainsKey("tcp:$port")) { throw "Forward $port was left active." }
    }
    if ($testState.Forwards['tcp:6000'] -ne 'tcp:7000') { throw 'An unrelated forward was changed.' }
}

try {
    foreach ($case in @(
        @{ Name = 'three present'; Ports = @(9099, 8080, 5001); Runs = 1 },
        @{ Name = 'partial'; Ports = @(8080, 5001); Runs = 1 },
        @{ Name = 'none'; Ports = @(); Runs = 1 },
        @{ Name = 'cleanup twice'; Ports = @(9099, 8080, 5001); Runs = 2 }
    )) {
        $testState.Forwards.Clear()
        $testState.Forwards['tcp:6000'] = 'tcp:7000'
        foreach ($port in $case.Ports) { $testState.Forwards["tcp:$port"] = "tcp:$port" }
        for ($run = 0; $run -lt $case.Runs; $run++) {
            $null = & $cleanupScript -Serial test-device -AdbPath Invoke-TestAdb -Remove
            Assert-Cleaned
        }
        Write-Output "PASS: $($case.Name)"
    }

    # A real removal error must be reported after trying the other ports.
    $testState.Forwards.Clear()
    $testState.Removed.Clear()
    foreach ($port in @(9099, 8080, 5001)) { $testState.Forwards["tcp:$port"] = "tcp:$port" }
    $testState.FailPort = 'tcp:9099'
    $failure = $null
    try { $null = & $cleanupScript -Serial test-device -AdbPath Invoke-TestAdb -Remove }
    catch { $failure = $_ }
    if (-not $failure -or $failure.Exception.Message -notmatch '9099') { throw 'ADB failure was hidden.' }
    if ($testState.Removed.Count -ne 3 -or $testState.Forwards.Count -ne 1) {
        throw 'Cleanup stopped before trying the other ports.'
    }
    Write-Output 'PASS: removal error propagates after other ports are processed'

    # Even an exit code zero cannot hide a forward left active by ADB.
    $testState.FailPort = $null
    $testState.KeepPort = 'tcp:9099'
    $failure = $null
    try { $null = & $cleanupScript -Serial test-device -AdbPath Invoke-TestAdb -Remove }
    catch { $failure = $_ }
    if (-not $failure -or $failure.Exception.Message -notmatch '9099') {
        throw 'Final verification accepted an active forward.'
    }
    Write-Output 'PASS: final verification rejects an active forward'
} finally {
    $global:LASTEXITCODE = $savedExitCode
}
