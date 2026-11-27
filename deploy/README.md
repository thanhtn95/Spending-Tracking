# Phase 3 — Cloud Run Deployment Guide ($0/month Free Tier)

## Prerequisites
- Google Cloud CLI (`gcloud`) authenticated: `gcloud auth login`
- A Neon PostgreSQL database (`DATABASE_URL` with `?sslmode=require`)
- A Gemini API key (`GEMINI_API_KEY`) from Google AI Studio
- A random `JWT_SECRET` (at least 32 characters)

## 1. One-Time GCP Project Setup (Tier 1 Free Region: `us-central1`)

```powershell
$PROJECT_ID = "your-gcp-project-id"
$REGION = "us-central1"
$REPO = "spending-tracker"

gcloud config set project $PROJECT_ID

# Enable required APIs
gcloud services enable `
    run.googleapis.com `
    artifactregistry.googleapis.com `
    secretmanager.googleapis.com `
    cloudbuild.googleapis.com

# Create Artifact Registry repository
gcloud artifacts repositories create $REPO `
    --repository-format=docker `
    --location=$REGION `
    --description="Spending Tracker container images"

# Apply cleanup policy to keep only the 3 most recent images (< 0.5 GB free tier)
gcloud artifacts repositories set-cleanup-policies $REPO `
    --location=$REGION `
    --policy=deploy/artifact-cleanup-policy.json
```

## 2. Store Secrets in Secret Manager

```powershell
# Create secrets (paste values when prompted, or pipe without trailing newline)
"postgresql://user:pass@host.neon.tech/db?sslmode=require" | gcloud secrets create DATABASE_URL --data-file=-
"your-gemini-api-key"                                       | gcloud secrets create GEMINI_API_KEY --data-file=-
"your-random-32-plus-char-jwt-secret"                       | gcloud secrets create JWT_SECRET --data-file=-

# Grant Cloud Run default compute service account read access to secrets
$PROJECT_NUMBER = gcloud projects describe $PROJECT_ID --format="value(projectNumber)"
$SA = "$PROJECT_NUMBER-compute@developer.gserviceaccount.com"

foreach ($secret in @("DATABASE_URL", "GEMINI_API_KEY", "JWT_SECRET")) {
    gcloud secrets add-iam-policy-binding $secret `
        --member="serviceAccount:$SA" `
        --role="roles/secretmanager.secretAccessor"
}
```

## 3. Build & Deploy to Cloud Run

```powershell
.\deploy\cloudrun-deploy.ps1 -ProjectId $PROJECT_ID -Region us-central1
```

## 4. Operational Guardrails
1. **Billing Alert:** In GCP Console → Billing → Budgets & Alerts, create a **$1/month** budget alert (50%, 90%, 100%).
2. **Lock Down Registration:** After registering your personal account via `POST /api/v1/auth/register`, redeploy with `-AllowRegistration "false"` so nobody else can register on your instance:
   ```powershell
   .\deploy\cloudrun-deploy.ps1 -ProjectId $PROJECT_ID -AllowRegistration "false"
   ```
