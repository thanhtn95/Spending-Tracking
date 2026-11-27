param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectId,

    [string]$Region = "us-central1",
    [string]$RepoName = "spending-tracker",
    [string]$ServiceName = "spending-tracker-api",
    [string]$AllowRegistration = "true"
)

$ErrorActionPreference = "Stop"

$ImageUri = "$Region-docker.pkg.dev/$ProjectId/$RepoName/${ServiceName}:latest"

Write-Host "==> Setting GCP project to $ProjectId" -ForegroundColor Cyan
gcloud config set project $ProjectId

Write-Host "==> Building and pushing image: $ImageUri" -ForegroundColor Cyan
gcloud builds submit ./backend --tag $ImageUri

Write-Host "==> Deploying $ServiceName to Cloud Run ($Region)" -ForegroundColor Cyan
gcloud run deploy $ServiceName `
    --image $ImageUri `
    --region $Region `
    --platform managed `
    --allow-unauthenticated `
    --port 8080 `
    --cpu 1 `
    --memory 256Mi `
    --min-instances 0 `
    --max-instances 2 `
    --concurrency 40 `
    --set-env-vars "ALLOW_REGISTRATION=$AllowRegistration,DEFAULT_CURRENCY=VND,RUN_MIGRATIONS=true,GEMINI_MODEL_IMAGE=gemini-3.5-flash-lite,GEMINI_MODEL_PDF=gemini-3.8-flash" `
    --set-secrets "DATABASE_URL=DATABASE_URL:latest,GEMINI_API_KEY=GEMINI_API_KEY:latest,JWT_SECRET=JWT_SECRET:latest"

Write-Host "==> Deployment complete!" -ForegroundColor Green
