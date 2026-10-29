param([string]$MySqlDirectory = 'C:\Program Files\MySQL\MySQL Server 8.0')

$ErrorActionPreference = 'Stop'
$backendDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$testDirectory = Join-Path $tempRoot ('speakmate-test-' + [guid]::NewGuid().ToString('N'))
$dataDirectory = Join-Path $testDirectory 'data'
$server = Join-Path $MySqlDirectory 'bin\mysqld.exe'
$client = Join-Path $MySqlDirectory 'bin\mysql.exe'
$admin = Join-Path $MySqlDirectory 'bin\mysqladmin.exe'
$testProcess = $null
$serverStarted = $false
$serverProcessId = $null
$previousLocation = Get-Location
$savedEnvironment = @{}

foreach ($key in @('TEST_DB_URL', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD', 'DEBUG')) {
    $savedEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}

try {
    # Chỉ dùng cổng và thư mục riêng của test, không đụng MySQL đang chạy của dự án.
    if (Get-NetTCPConnection -LocalPort 13317 -State Listen -ErrorAction SilentlyContinue) {
        throw 'Port 13317 is occupied. This script will not connect to or shut down an existing server.'
    }
    New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
    $commonArguments = @('--no-defaults', ('--basedir="' + $MySqlDirectory + '"'), ('--datadir="' + $dataDirectory + '"'))

    $initialize = Start-Process -FilePath $server `
        -ArgumentList ($commonArguments + '--initialize-insecure') `
        -PassThru -Wait -WindowStyle Hidden `
        -RedirectStandardError (Join-Path $testDirectory 'initialize.log')

    if ($initialize.ExitCode -ne 0) {
        throw 'Disposable MySQL initialization failed.'
    }

    $testProcess = Start-Process -FilePath $server `
        -ArgumentList ($commonArguments + @('--bind-address=127.0.0.1', '--port=13317', '--mysqlx=OFF', '--console')) `
        -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $testDirectory 'server.log') `
        -RedirectStandardError (Join-Path $testDirectory 'server-error.log')

    $connection = @(
        '--no-defaults',
        '--default-character-set=utf8mb4',
        '--protocol=TCP',
        '--host=127.0.0.1',
        '--port=13317',
        '--user=root',
        '--connect-timeout=2'
    )
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $ErrorActionPreference = 'Continue'
        & $admin @connection ping --silent 2>$null | Out-Null
        $pingExitCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($pingExitCode -eq 0) {
            $ready = $true
            break
        }

        Start-Sleep -Milliseconds 500
    }

    if (-not $ready) {
        throw 'Disposable MySQL did not become ready.'
    }

    # Xác nhận đúng datadir trước khi nhập SQL hoặc gửi lệnh tắt server.
    $actualDirectory = (& $client @connection --batch --raw --skip-column-names --execute='SELECT @@datadir').Trim().Replace('/', '\').TrimEnd('\')
    if ($LASTEXITCODE -ne 0 -or $actualDirectory -ne $dataDirectory.TrimEnd('\')) {
        throw 'Refusing to use an unexpected MySQL datadir.'
    }

    $serverStarted = $true
    $pidFile = (& $client @connection --batch --raw --skip-column-names --execute='SELECT @@pid_file').Trim()
    $serverProcessId = [int]([System.IO.File]::ReadAllText($pidFile).Trim())
    $schema = [System.IO.File]::ReadAllText((Join-Path $backendDirectory '..\speakmate.sql')).Replace('speakmate', 'speakmate_test')
    $importFile = Join-Path $testDirectory 'schema.sql'
    [System.IO.File]::WriteAllText($importFile, $schema, [System.Text.UTF8Encoding]::new($false))
    $import = Start-Process -FilePath $client `
        -ArgumentList ($connection + '--default-character-set=utf8mb4') `
        -PassThru -Wait -WindowStyle Hidden `
        -RedirectStandardInput $importFile `
        -RedirectStandardOutput (Join-Path $testDirectory 'import.log') `
        -RedirectStandardError (Join-Path $testDirectory 'import-error.log')

    if ($import.ExitCode -ne 0) {
        throw 'Schema import failed.'
    }

    $env:TEST_DB_URL = 'jdbc:mysql://127.0.0.1:13317/speakmate_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
    $env:TEST_DB_USERNAME = 'root'
    $env:TEST_DB_PASSWORD = ''
    $env:DEBUG = ''
    Set-Location -LiteralPath $backendDirectory
    & .\gradlew.bat build mysqlTest --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw 'Backend checks failed. See build/reports/tests.'
    }
} finally {
    Set-Location -LiteralPath $previousLocation
    foreach ($key in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $savedEnvironment[$key], 'Process')
    }

    if ($serverStarted) {
        & $admin @connection shutdown 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning ('Could not stop disposable MySQL. Inspect ' + $testDirectory)
        }

        if ($null -ne $serverProcessId) {
            $stopping = Get-Process -Id $serverProcessId -ErrorAction SilentlyContinue
            if ($null -ne $stopping -and -not $stopping.WaitForExit(30000)) {
                throw ('Disposable MySQL is still stopping; preserving its datadir: ' + $testDirectory)
            }
        }
    } elseif ($null -ne $testProcess -and -not $testProcess.HasExited) {
        # Chỉ dừng tiến trình do script này tạo.
        Stop-Process -Id $testProcess.Id -ErrorAction SilentlyContinue
    }

    # Chỉ xóa thư mục test đã xác thực nằm bên trong TEMP.
    $resolvedTestDirectory = [System.IO.Path]::GetFullPath($testDirectory)
    if (-not $resolvedTestDirectory.StartsWith($tempRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase) -or
            [System.IO.Path]::GetFileName($resolvedTestDirectory) -notmatch '^speakmate-test-[0-9a-f]{32}$') {
        throw 'Unsafe temporary cleanup target.'
    }
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        try {
            if (Test-Path -LiteralPath $resolvedTestDirectory) {
                Remove-Item -LiteralPath $resolvedTestDirectory -Recurse -Force
            }
            break
        } catch {
            if ($attempt -eq 39) {
                Write-Warning ('Temporary files still locked: ' + $resolvedTestDirectory)
            }
            Start-Sleep -Milliseconds 500
        }
    }
}
