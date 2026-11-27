use crate::models::{ExtractedTransaction, GeminiExtractionResult};
use chrono::NaiveDate;
use rust_decimal::Decimal;
use std::str::FromStr;

/// Attempts to parse a standard bank statement CSV directly in Rust (0 Gemini quota used).
/// Returns `Some(GeminiExtractionResult)` if headers match common bank CSV columns
/// (`date`, `description`/`vendor`/`merchant`, `amount` or `debit`/`credit`),
/// or `None` to fall back to Gemini AI for unusual CSV layouts.
pub fn try_parse_bank_csv(bytes: &[u8], default_currency: &str) -> Option<GeminiExtractionResult> {
    let text = std::str::from_utf8(bytes).ok()?;
    let mut lines = text
        .lines()
        .map(str::trim)
        .filter(|l| !l.is_empty());

    let header_line = lines.next()?.trim_start_matches('\u{feff}');
    let delimiter = if header_line.contains(';') && !header_line.contains(',') {
        ';'
    } else {
        ','
    };

    let headers: Vec<String> = split_csv_line(header_line, delimiter)
        .into_iter()
        .map(|h| h.trim().to_lowercase())
        .collect();

    let date_idx = headers.iter().position(|h| {
        h == "date" || h.contains("tx_date") || h.contains("trans date") || h.contains("posting date") || h.contains("ngày")
    })?;

    let vendor_idx = headers.iter().position(|h| {
        h.contains("merchant")
            || h.contains("vendor")
            || h.contains("payee")
            || h.contains("description")
            || h.contains("details")
            || h.contains("nội dung")
            || h.contains("memo")
    })?;

    let desc_idx = headers.iter().position(|h| {
        h != &headers[vendor_idx] && (h.contains("description") || h.contains("note") || h.contains("memo"))
    });

    let amount_idx = headers.iter().position(|h| h == "amount" || h.contains("số tiền"));
    let debit_idx = headers.iter().position(|h| h.contains("debit") || h.contains("outflow") || h.contains("withdrawal") || h.contains("chi"));
    let credit_idx = headers.iter().position(|h| h.contains("credit") || h.contains("inflow") || h.contains("deposit") || h.contains("thu"));
    let currency_idx = headers.iter().position(|h| h == "currency" || h == "ccy");

    if amount_idx.is_none() && debit_idx.is_none() && credit_idx.is_none() {
        return None;
    }

    let mut transactions = Vec::new();
    for line in lines {
        let cols = split_csv_line(line, delimiter);
        if cols.len() <= date_idx || cols.len() <= vendor_idx {
            continue;
        }

        let Some(date) = parse_flexible_date(cols[date_idx].trim()) else {
            continue;
        };

        let vendor = cols[vendor_idx].trim();
        if vendor.is_empty() {
            continue;
        }

        let description = desc_idx
            .and_then(|i| cols.get(i))
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty());

        let currency = currency_idx
            .and_then(|i| cols.get(i))
            .map(|s| s.trim().to_uppercase())
            .filter(|s| s.len() == 3)
            .unwrap_or_else(|| default_currency.to_string());

        let (amount, tx_type) = if let (Some(di), Some(ci)) = (debit_idx, credit_idx) {
            let d = cols.get(di).and_then(|s| parse_decimal(s)).unwrap_or(Decimal::ZERO);
            let c = cols.get(ci).and_then(|s| parse_decimal(s)).unwrap_or(Decimal::ZERO);
            if c > Decimal::ZERO && d <= Decimal::ZERO {
                (c.abs(), "credit")
            } else if d > Decimal::ZERO {
                (d.abs(), "debit")
            } else {
                continue;
            }
        } else if let Some(ai) = amount_idx {
            let Some(val) = cols.get(ai).and_then(|s| parse_decimal(s)) else {
                continue;
            };
            if val.is_zero() {
                continue;
            }
            if val < Decimal::ZERO {
                (val.abs(), "debit")
            } else {
                // Positive in single-amount CSVs: check if a "type" column exists
                let type_col = headers
                    .iter()
                    .position(|h| h == "type" || h == "tx_type")
                    .and_then(|i| cols.get(i))
                    .map(|s| s.trim().to_lowercase());
                match type_col.as_deref() {
                    Some("credit") | Some("inflow") | Some("cr") => (val.abs(), "credit"),
                    Some("debit") | Some("outflow") | Some("dr") => (val.abs(), "debit"),
                    _ => (val.abs(), "credit"),
                }
            }
        } else {
            continue;
        };

        transactions.push(ExtractedTransaction {
            date: date.format("%Y-%m-%d").to_string(),
            vendor: vendor.to_string(),
            description,
            amount,
            tx_type: tx_type.to_string(),
            category: "Uncategorized".to_string(),
            confidence: 1.0,
            currency: Some(currency),
        });
    }

    if transactions.is_empty() {
        return None;
    }

    Some(GeminiExtractionResult {
        transactions,
        opening_balance: None,
        closing_balance: None,
    })
}

fn split_csv_line(line: &str, delimiter: char) -> Vec<String> {
    let mut fields = Vec::new();
    let mut current = String::new();
    let mut in_quotes = false;
    let mut chars = line.chars().peekable();

    while let Some(c) = chars.next() {
        match c {
            '"' => {
                if in_quotes && chars.peek() == Some(&'"') {
                    chars.next();
                    current.push('"');
                } else {
                    in_quotes = !in_quotes;
                }
            }
            ch if ch == delimiter && !in_quotes => {
                fields.push(std::mem::take(&mut current));
            }
            _ => current.push(c),
        }
    }
    fields.push(current);
    fields
}

fn parse_decimal(raw: &str) -> Option<Decimal> {
    let cleaned: String = raw
        .trim()
        .replace(',', "")
        .replace(' ', "")
        .replace('$', "")
        .replace('₫', "");
    if cleaned.is_empty() {
        return None;
    }
    Decimal::from_str(&cleaned).ok()
}

fn parse_flexible_date(raw: &str) -> Option<NaiveDate> {
    let s = raw.split_whitespace().next().unwrap_or(raw);
    for fmt in ["%Y-%m-%d", "%d/%m/%Y", "%m/%d/%Y", "%Y/%m/%d", "%d-%m-%Y"] {
        if let Ok(d) = NaiveDate::parse_from_str(s, fmt) {
            return Some(d);
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_standard_debit_credit_csv() {
        let csv = b"Date,Description,Debit,Credit,Currency\n2026-11-10,\"Highlands Coffee\",55000,,VND\n2026-11-12,\"Company Salary\",,25000000,VND\n";
        let res = try_parse_bank_csv(csv, "VND").expect("should parse csv");
        assert_eq!(res.transactions.len(), 2);
        assert_eq!(res.transactions[0].vendor, "Highlands Coffee");
        assert_eq!(res.transactions[0].tx_type, "debit");
        assert_eq!(res.transactions[1].tx_type, "credit");
    }

    #[test]
    fn returns_none_for_unrecognized_csv() {
        let csv = b"Foo,Bar\n1,2\n";
        assert!(try_parse_bank_csv(csv, "VND").is_none());
    }
}
