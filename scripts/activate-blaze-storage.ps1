$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$firebase = Join-Path $env:APPDATA "npm\firebase.cmd"

if (-not (Test-Path -LiteralPath $firebase)) {
    throw "Firebase CLI was not found. Install it with: npm install -g firebase-tools"
}

$requiredFiles = @(
    (Join-Path $projectRoot "firebase.json"),
    (Join-Path $projectRoot "storage.rules"),
    (Join-Path $projectRoot ".firebaserc")
)

foreach ($file in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $file)) {
        throw "Required Firebase file is missing: $file"
    }
}

Write-Host "Before continuing, confirm that:" -ForegroundColor Cyan
Write-Host "  1. Blaze billing is active for cbu-lost-and-found."
Write-Host "  2. Firebase Console > Storage > Get started has created the default bucket."
Write-Host "  3. A Google Cloud budget and alerts are configured."

$confirmation = Read-Host "Type DEPLOY to publish Storage rules"
if ($confirmation -cne "DEPLOY") {
    Write-Host "Cancelled. No Firebase resources were changed."
    exit 0
}

Push-Location $projectRoot
try {
    & $firebase deploy --only storage --project cbu-lost-and-found
    if ($LASTEXITCODE -ne 0) {
        throw "Firebase Storage rule deployment failed with exit code $LASTEXITCODE."
    }
    Write-Host "Storage rules deployed successfully." -ForegroundColor Green
} finally {
    Pop-Location
}
