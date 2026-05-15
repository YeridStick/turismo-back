param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot
)

$ErrorActionPreference = "Stop"

Set-Location -LiteralPath $ProjectRoot

# Detiene daemons de Gradle
.\gradlew --stop

# Borra jars generados que suelen quedar bloqueados
Get-ChildItem -LiteralPath $ProjectRoot -Recurse -File -Filter *.jar |
    Where-Object { $_.FullName -match '\\build\\libs\\' } |
    ForEach-Object {
        Remove-Item -LiteralPath $_.FullName -Force -ErrorAction SilentlyContinue
    }

# Reintenta compilación sin daemon
.\gradlew --no-daemon :app-service:classes
