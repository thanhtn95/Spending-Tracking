use crate::error::AppError;
use lopdf::Document;

/// Inspects a PDF byte slice. If it is encrypted, decrypts it in-memory using
/// `password` and returns the unencrypted PDF bytes. If it is already unencrypted,
/// returns the original bytes unchanged.
pub fn decrypt_pdf_if_needed(bytes: &[u8], password: Option<&str>) -> Result<Vec<u8>, AppError> {
    let mut doc = Document::load_mem(bytes)
        .map_err(|e| AppError::Unprocessable(format!("Invalid or unreadable PDF: {e}")))?;

    if !doc.is_encrypted() {
        return Ok(bytes.to_vec());
    }

    let pwd = password
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .ok_or_else(|| {
            AppError::Unprocessable(
                "PDF is password-protected; please provide `pdf_password`".to_string(),
            )
        })?;

    doc.decrypt(pwd).map_err(|e| {
        AppError::Unprocessable(format!("Failed to decrypt PDF (check password): {e}"))
    })?;

    let mut out = Vec::new();
    doc.save_to(&mut out)
        .map_err(|e| AppError::Internal(format!("Failed to serialize decrypted PDF: {e}")))?;

    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_invalid_pdf_bytes() {
        let res = decrypt_pdf_if_needed(b"not a pdf", None);
        assert!(matches!(res, Err(AppError::Unprocessable(_))));
    }

    #[test]
    fn passes_through_unencrypted_pdf() {
        let mut doc = Document::with_version("1.5");
        let mut bytes = Vec::new();
        doc.save_to(&mut bytes).expect("save minimal pdf");

        let out = decrypt_pdf_if_needed(&bytes, None).expect("should pass through");
        assert_eq!(out, bytes);
    }
}
