param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,
    [string]$AdbPath = 'adb',
    [switch]$Remove
)

$ErrorActionPreference = 'Stop'
if (-not (Get-Command $AdbPath -ErrorAction SilentlyContinue)) {
    throw 'ADB não encontrado. Informe -AdbPath com o caminho do SDK/platform-tools/adb.'
}
$deviceState = & $AdbPath -s $Serial get-state
if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') {
    throw "Dispositivo $Serial indisponível ou não autorizado. Confira adb devices."
}

$removalFailures = @()
if ($Remove) {
    $forwards = & $AdbPath -s $Serial reverse --list
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível verificar os encaminhamentos.' }
}

foreach ($port in @(9099, 8080)) {
    if ($Remove) {
        # Match the device-side port, regardless of its destination.
        if (($forwards -join "`n") -notmatch "(?m)^\S+\s+tcp:$port\s+") { continue }
        & $AdbPath -s $Serial reverse --remove "tcp:$port"
        if ($LASTEXITCODE -ne 0) {
            $removalFailures += "Falha ao remover a porta $port (ADB: $LASTEXITCODE)."
        }
    } else {
        & $AdbPath -s $Serial reverse "tcp:$port" "tcp:$port"
        if ($LASTEXITCODE -ne 0) { throw "Falha no encaminhamento da porta $port." }
    }
}

$forwards = & $AdbPath -s $Serial reverse --list
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível verificar os encaminhamentos.' }
foreach ($port in @(9099, 8080)) {
    if ($Remove) {
        if (($forwards -join "`n") -match "(?m)^\S+\s+tcp:$port\s+") {
            $removalFailures += "Encaminhamento ainda ativo na porta $port."
        }
    } else {
        $present = ($forwards -join "`n") -match "\s+tcp:$port\s+tcp:$port(\s|$)"
        if (-not $present) { throw "Encaminhamento inesperado na porta $port." }
    }
}
if ($removalFailures.Count -gt 0) { throw ($removalFailures -join ' ') }
$forwards
