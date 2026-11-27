# Spending Tracker — Android Client

Kotlin + Jetpack Compose (Material 3) Android app for the Personal Spending Tracker.

## Features
- **Authentication:** Email/password Login & Register (`POST /api/v1/auth/login` & `/register`), encrypted JWT storage (`EncryptedSharedPreferences`), and automatic `Authorization: Bearer <token>` injection.
- **ML Kit Document Scanner:** Scans paper receipts/bills with auto-crop & glare removal.
- **Photo Picker & Statement Picker:** Multi-select banking screenshots (downscaled to max 1600px JPEG before upload) and PDF/CSV bank statements.
- **Android Share Sheet (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`):** Share screenshots, PDFs, or CSVs directly from banking apps into Spending Tracker.
- **Password-Protected PDFs:** Automatically prompts for `pdf_password` when the backend detects an encrypted statement PDF.
- **Draft Confirmation Dialog:** Displays statement balance reconciliation status, highlights ±2-day fuzzy duplicates, and lets you edit vendor/amount/date/category or discard rows before confirming.
- **Monthly Dashboard:** Month-by-month total spending, category breakdown bars, and confirmed transaction list.

## Opening & Running
1. Open the `android/` folder in **Android Studio** (Ladybug / 2024.2+ with JDK 17).
2. Update `API_BASE_URL` in `app/build.gradle.kts` to your deployed Cloud Run URL (defaults to `http://10.0.2.2:8080/` for local Android Emulator testing).
3. Sync Gradle and run on an emulator or physical Android device (minSdk 26 / Android 8.0+).
