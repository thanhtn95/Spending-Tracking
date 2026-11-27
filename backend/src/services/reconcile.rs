use crate::models::ExtractedTransaction;
use chrono::NaiveDate;
use rust_decimal::Decimal;
use sha2::{Digest, Sha256};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ReconciliationOutcome {
    pub reconciled: bool,
    pub diff: Option<Decimal>,
}

/// Verifies bank statement math:
/// `opening_balance - total_debits + total_credits == closing_balance`
/// Allows up to `0.01` rounding tolerance.
pub fn reconcile_statement(
    opening_balance: Option<Decimal>,
    closing_balance: Option<Decimal>,
    transactions: &[ExtractedTransaction],
) -> ReconciliationOutcome {
    let (Some(opening), Some(closing)) = (opening_balance, closing_balance) else {
        return ReconciliationOutcome {
            reconciled: false,
            diff: None,
        };
    };

    let mut total_debits = Decimal::ZERO;
    let mut total_credits = Decimal::ZERO;

    for tx in transactions {
        let amt = tx.amount.abs();
        if tx.tx_type.eq_ignore_ascii_case("credit") {
            total_credits += amt;
        } else {
            total_debits += amt;
        }
    }

    let expected_closing = opening - total_debits + total_credits;
    let diff = expected_closing - closing;
    let tolerance = Decimal::new(1, 2); // 0.01

    ReconciliationOutcome {
        reconciled: diff.abs() <= tolerance,
        diff: Some(diff),
    }
}

/// Computes SHA-256 hex digest across one or more uploaded file byte slices.
pub fn compute_file_hash(files: &[&[u8]]) -> String {
    let mut hasher = Sha256::new();
    for (idx, chunk) in files.iter().enumerate() {
        hasher.update((idx as u64).to_be_bytes());
        hasher.update((chunk.len() as u64).to_be_bytes());
        hasher.update(chunk);
    }
    hex::encode(hasher.finalize())
}

/// Normalizes text by lowercasing and collapsing consecutive whitespace.
pub fn normalize_text(input: &str) -> String {
    input
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
        .to_lowercase()
}

/// Deterministic SHA-256 hash of `(tx_date, normalized_description, amount, tx_type, source)`
/// to prevent duplicate statement/receipt row ingestion.
pub fn compute_dedup_hash(
    tx_date: NaiveDate,
    description_or_vendor: &str,
    amount: Decimal,
    tx_type: &str,
    source: &str,
) -> String {
    let norm_desc = normalize_text(description_or_vendor);
    let norm_amount = amount.abs().normalize().to_string();
    let payload = format!(
        "{}|{}|{}|{}|{}",
        tx_date.format("%Y-%m-%d"),
        norm_desc,
        norm_amount,
        tx_type.trim().to_lowercase(),
        source.trim().to_lowercase()
    );
    let mut hasher = Sha256::new();
    hasher.update(payload.as_bytes());
    hex::encode(hasher.finalize())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::str::FromStr;

    fn sample_tx(amount: &str, tx_type: &str) -> ExtractedTransaction {
        ExtractedTransaction {
            date: "2026-11-15".to_string(),
            vendor: "Test Vendor".to_string(),
            description: Some("Test".to_string()),
            amount: Decimal::from_str(amount).unwrap(),
            tx_type: tx_type.to_string(),
            category: "Groceries".to_string(),
            confidence: 0.95,
            currency: Some("VND".to_string()),
        }
    }

    #[test]
    fn statement_reconciles_exact_match() {
        let txs = vec![
            sample_tx("150000.00", "debit"),
            sample_tx("50000.00", "debit"),
            sample_tx("500000.00", "credit"),
        ];
        let res = reconcile_statement(
            Some(Decimal::from_str("1000000.00").unwrap()),
            Some(Decimal::from_str("1300000.00").unwrap()),
            &txs,
        );
        assert!(res.reconciled);
        assert_eq!(res.diff, Some(Decimal::ZERO));
    }

    #[test]
    fn statement_flags_discrepancy() {
        let txs = vec![sample_tx("100.00", "debit")];
        let res = reconcile_statement(
            Some(Decimal::from_str("1000.00").unwrap()),
            Some(Decimal::from_str("850.00").unwrap()),
            &txs,
        );
        assert!(!res.reconciled);
        assert_eq!(res.diff, Some(Decimal::from_str("50.00").unwrap()));
    }

    #[test]
    fn dedup_hash_is_deterministic_across_whitespace_and_scale() {
        let d = NaiveDate::from_ymd_opt(2026, 11, 20).unwrap();
        let h1 = compute_dedup_hash(
            d,
            "  Highlands   Coffee  ",
            Decimal::from_str("55000.00").unwrap(),
            "debit",
            "bill",
        );
        let h2 = compute_dedup_hash(
            d,
            "highlands coffee",
            Decimal::from_str("55000").unwrap(),
            "DEBIT",
            "BILL",
        );
        assert_eq!(h1, h2);
    }
}
