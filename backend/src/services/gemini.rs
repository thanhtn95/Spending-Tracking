use crate::{config::Config, error::AppError, models::GeminiExtractionResult};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine};
use reqwest::Client;
use serde::Deserialize;
use serde_json::{json, Value};

use std::time::Duration;

#[derive(Debug, Clone)]
pub struct MediaPart {
    pub mime_type: String,
    pub bytes: Vec<u8>,
}

#[derive(Clone)]
pub struct GeminiClient {
    http: Client,
    api_key: String,
    api_base: String,
    model_image: String,
    model_pdf: String,
}

impl GeminiClient {
    pub fn new(config: &Config) -> Self {
        let http = Client::builder()
            .timeout(Duration::from_secs(180))
            .build()
            .unwrap_or_else(|_| Client::new());

        Self {
            http,
            api_key: config.gemini_api_key.clone(),
            api_base: config.gemini_api_base.trim_end_matches('/').to_string(),
            model_image: config.gemini_model_image.clone(),
            model_pdf: config.gemini_model_pdf.clone(),
        }
    }

    /// Selects the primary Gemini model according to the spec:
    /// - Multi-page PDF bank statements -> Gemini 3.8 Flash (`model_pdf`)
    /// - Receipts, screenshots, CSVs -> Gemini 3.5 Flash-Lite (`model_image`)
    #[allow(dead_code)]
    pub fn select_model<'a>(&'a self, parts: &[MediaPart]) -> &'a str {
        if parts.iter().any(|p| p.mime_type == "application/pdf") {
            &self.model_pdf
        } else {
            &self.model_image
        }
    }

    /// Builds an ordered list of models to try: primary spec model first,
    /// followed by fast, high-availability Flash-Lite fallbacks if the primary
    /// returns 503/429/404 or times out under high demand.
    fn candidate_models(&self, parts: &[MediaPart]) -> Vec<String> {
        let is_pdf = parts.iter().any(|p| p.mime_type == "application/pdf");
        let defaults: &[&str] = if is_pdf {
            &[
                &self.model_pdf,
                &self.model_image,
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite",
            ]
        } else {
            &[
                &self.model_image,
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite",
            ]
        };

        let mut models = Vec::with_capacity(defaults.len());
        for m in defaults {
            let s = (*m).to_string();
            if !models.contains(&s) {
                models.push(s);
            }
        }
        models
    }

    /// Extracts structured transactions (and statement balances when present)
    /// using Gemini Structured Outputs, with automatic fallback on 503/429/timeout.
    pub async fn extract_transactions(
        &self,
        parts: &[MediaPart],
        source_type: &str,
        allowed_categories: &[String],
    ) -> Result<(GeminiExtractionResult, String), AppError> {
        let prompt = build_extraction_prompt(source_type, allowed_categories);
        let mut content_parts: Vec<Value> = Vec::with_capacity(parts.len() + 1);
        content_parts.push(json!({ "text": prompt }));

        for part in parts {
            if part.mime_type == "text/csv" || part.mime_type.starts_with("text/") {
                let text = String::from_utf8_lossy(&part.bytes);
                content_parts.push(json!({
                    "text": format!("--- ATTACHED FILE ({}) ---\n{}", part.mime_type, text)
                }));
            } else {
                content_parts.push(json!({
                    "inline_data": {
                        "mime_type": part.mime_type,
                        "data": BASE64.encode(&part.bytes),
                    }
                }));
            }
        }

        let request_body = json!({
            "contents": [{
                "role": "user",
                "parts": content_parts
            }],
            "generationConfig": {
                "temperature": 0.1,
                "responseMimeType": "application/json",
                "responseSchema": extraction_response_schema(allowed_categories)
            }
        });

        let models = self.candidate_models(parts);
        let mut last_error: Option<AppError> = None;

        for (idx, model) in models.iter().enumerate() {
            let url = format!(
                "{}/models/{}:generateContent?key={}",
                self.api_base, model, self.api_key
            );

            let send_result = self.http.post(&url).json(&request_body).send().await;
            let resp = match send_result {
                Ok(r) => r,
                Err(e) => {
                    tracing::warn!(model = %model, error = %e, "Gemini request failed; trying fallback if available");
                    last_error = Some(AppError::Gemini(format!(
                        "HTTP request to Gemini ({model}) failed: {e}"
                    )));
                    continue;
                }
            };

            let status = resp.status();
            let body_text = resp
                .text()
                .await
                .map_err(|e| AppError::Gemini(format!("Failed to read Gemini response: {e}")))?;

            if status.is_success() {
                let parsed = parse_gemini_response(&body_text)?;
                return Ok((parsed, model.clone()));
            }

            let is_retryable = matches!(status.as_u16(), 404 | 429 | 500 | 503);
            let err = AppError::Gemini(format!(
                "Gemini API ({model}) returned {status}: {body_text}"
            ));

            if is_retryable && idx + 1 < models.len() {
                tracing::warn!(model = %model, status = %status, "Gemini model overloaded/rate-limited; falling back to next model");
                last_error = Some(err);
                continue;
            }

            return Err(err);
        }

        Err(last_error.unwrap_or_else(|| AppError::Gemini("All Gemini models failed".to_string())))
    }

    /// Generates 3 concise, actionable personal finance insights from aggregated
    /// monthly summary text using Flash-Lite (1 tiny text request).
    pub async fn generate_text_insights(
        &self,
        summary_prompt: &str,
    ) -> Result<(Vec<String>, String), AppError> {
        let request_body = json!({
            "contents": [{
                "role": "user",
                "parts": [{ "text": summary_prompt }]
            }],
            "generationConfig": {
                "temperature": 0.3,
                "responseMimeType": "application/json",
                "responseSchema": {
                    "type": "OBJECT",
                    "properties": {
                        "tips": {
                            "type": "ARRAY",
                            "items": { "type": "STRING" }
                        }
                    },
                    "required": ["tips"]
                }
            }
        });

        let models = [&self.model_image, "gemini-3.5-flash-lite", "gemini-3.1-flash-lite"];
        let mut tried = Vec::new();
        let mut last_err: Option<AppError> = None;

        for m in models {
            let model = m.to_string();
            if tried.contains(&model) {
                continue;
            }
            tried.push(model.clone());

            let url = format!(
                "{}/models/{}:generateContent?key={}",
                self.api_base, model, self.api_key
            );
            let resp = match self.http.post(&url).json(&request_body).send().await {
                Ok(r) => r,
                Err(e) => {
                    last_err = Some(AppError::Gemini(format!("Gemini insights request failed: {e}")));
                    continue;
                }
            };
            let status = resp.status();
            let body_text = resp
                .text()
                .await
                .map_err(|e| AppError::Gemini(format!("Failed reading Gemini insights: {e}")))?;

            if status.is_success() {
                let envelope: GeminiGenerateResponse = serde_json::from_str(&body_text)
                    .map_err(|e| AppError::Gemini(format!("Invalid Gemini envelope: {e}")))?;
                let text = envelope
                    .candidates
                    .into_iter()
                    .filter_map(|c| c.content)
                    .flat_map(|c| c.parts)
                    .find_map(|p| p.text)
                    .ok_or_else(|| AppError::Gemini("Empty Gemini insight response".to_string()))?;

                #[derive(Deserialize)]
                struct TipsEnvelope {
                    tips: Vec<String>,
                }
                let parsed: TipsEnvelope = serde_json::from_str(&text)
                    .map_err(|e| AppError::Gemini(format!("Invalid tips JSON: {e}")))?;
                return Ok((parsed.tips, model));
            }

            if matches!(status.as_u16(), 404 | 429 | 500 | 503) {
                last_err = Some(AppError::Gemini(format!("Gemini ({model}) returned {status}")));
                continue;
            }
            return Err(AppError::Gemini(format!("Gemini ({model}) returned {status}: {body_text}")));
        }

        Err(last_err.unwrap_or_else(|| AppError::Gemini("All Gemini insight models failed".to_string())))
    }
}

fn build_extraction_prompt(source_type: &str, categories: &[String]) -> String {
    let cat_list = categories.join(", ");
    format!(
        "You are a financial document parser for a personal spending tracker.\n\
         Input source type: {source_type}.\n\
         Extract every transaction from the attached document(s) or image(s).\n\
         Rules:\n\
         1. Format `date` strictly as YYYY-MM-DD.\n\
         2. `amount` MUST be a positive number (no currency symbols or commas).\n\
         3. Set `type` to \"debit\" for money spent/outflows and \"credit\" for refunds/inflows.\n\
         4. Choose `category` strictly from: [{cat_list}]. Use \"Uncategorized\" if unsure.\n\
         5. Provide `confidence` between 0.0 and 1.0.\n\
         6. For bank statements, extract `opening_balance` and `closing_balance`; otherwise set them to null.\n\
         7. Detect 3-letter ISO `currency` (e.g. VND, USD) when visible."
    )
}

fn extraction_response_schema(categories: &[String]) -> Value {
    let mut category_schema = json!({ "type": "STRING" });
    if !categories.is_empty() {
        category_schema["enum"] = json!(categories);
    }

    json!({
        "type": "OBJECT",
        "properties": {
            "opening_balance": { "type": "NUMBER", "nullable": true },
            "closing_balance": { "type": "NUMBER", "nullable": true },
            "transactions": {
                "type": "ARRAY",
                "items": {
                    "type": "OBJECT",
                    "properties": {
                        "date": { "type": "STRING", "description": "YYYY-MM-DD" },
                        "vendor": { "type": "STRING" },
                        "description": { "type": "STRING", "nullable": true },
                        "amount": { "type": "NUMBER" },
                        "type": { "type": "STRING", "enum": ["debit", "credit"] },
                        "category": category_schema,
                        "confidence": { "type": "NUMBER" },
                        "currency": { "type": "STRING", "nullable": true }
                    },
                    "required": ["date", "vendor", "amount", "type", "category", "confidence"]
                }
            }
        },
        "required": ["transactions"]
    })
}

#[derive(Debug, Deserialize)]
struct GeminiGenerateResponse {
    #[serde(default)]
    candidates: Vec<GeminiCandidate>,
}

#[derive(Debug, Deserialize)]
struct GeminiCandidate {
    content: Option<GeminiContent>,
}

#[derive(Debug, Deserialize)]
struct GeminiContent {
    #[serde(default)]
    parts: Vec<GeminiTextPart>,
}

#[derive(Debug, Deserialize)]
struct GeminiTextPart {
    text: Option<String>,
}

pub fn parse_gemini_response(raw_body: &str) -> Result<GeminiExtractionResult, AppError> {
    let envelope: GeminiGenerateResponse = serde_json::from_str(raw_body)
        .map_err(|e| AppError::Gemini(format!("Invalid Gemini envelope JSON: {e}")))?;

    let text = envelope
        .candidates
        .into_iter()
        .filter_map(|c| c.content)
        .flat_map(|c| c.parts)
        .find_map(|p| p.text)
        .ok_or_else(|| AppError::Gemini("Gemini returned no text candidate".to_string()))?;

    serde_json::from_str::<GeminiExtractionResult>(&text).map_err(|e| {
        AppError::Gemini(format!(
            "Failed to parse Gemini structured output JSON: {e}; raw={text}"
        ))
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_config() -> Config {
        Config {
            database_url: "postgres://localhost/test".to_string(),
            gemini_api_key: "test-key".to_string(),
            jwt_secret: "test_secret_key_that_is_at_least_32_chars".to_string(),
            jwt_expiration_hours: 168,
            allow_registration: true,
            gemini_model_image: "gemini-3.5-flash-lite".to_string(),
            gemini_model_pdf: "gemini-3.8-flash".to_string(),
            gemini_api_base: "https://generativelanguage.googleapis.com/v1beta".to_string(),
            default_currency: "VND".to_string(),
            port: 8080,
            run_migrations: false,
        }
    }

    #[test]
    fn routes_images_to_flash_lite_and_pdfs_to_flash() {
        let client = GeminiClient::new(&test_config());

        let img_parts = vec![MediaPart {
            mime_type: "image/jpeg".to_string(),
            bytes: vec![1, 2, 3],
        }];
        assert_eq!(client.select_model(&img_parts), "gemini-3.5-flash-lite");

        let pdf_parts = vec![MediaPart {
            mime_type: "application/pdf".to_string(),
            bytes: vec![1, 2, 3],
        }];
        assert_eq!(client.select_model(&pdf_parts), "gemini-3.8-flash");
    }

    #[test]
    fn parses_structured_output_envelope() {
        let envelope = json!({
            "candidates": [{
                "content": {
                    "parts": [{
                        "text": "{\"opening_balance\": 1000.0, \"closing_balance\": 950.0, \"transactions\": [{\"date\": \"2026-11-20\", \"vendor\": \"Cafe\", \"description\": \"Latte\", \"amount\": 50.0, \"type\": \"debit\", \"category\": \"Dining\", \"confidence\": 0.98, \"currency\": \"USD\"}]}"
                    }]
                }
            }]
        })
        .to_string();

        let result = parse_gemini_response(&envelope).expect("should parse");
        assert_eq!(result.transactions.len(), 1);
        assert_eq!(result.transactions[0].vendor, "Cafe");
        assert_eq!(result.transactions[0].category, "Dining");
    }
}
