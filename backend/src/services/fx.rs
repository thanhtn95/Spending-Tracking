use reqwest::Client;
use rust_decimal::Decimal;
use serde::Deserialize;
use std::{
    collections::HashMap,
    str::FromStr,
    sync::Arc,
    time::{Duration, Instant},
};
use tokio::sync::RwLock;

const CACHE_TTL: Duration = Duration::from_secs(12 * 3600);

#[derive(Clone)]
pub struct FxService {
    http: Client,
    /// Rates relative to 1 USD (e.g. "VND" -> 25400.0)
    cache: Arc<RwLock<Option<(Instant, HashMap<String, Decimal>)>>>,
}

#[derive(Deserialize)]
struct OpenErResponse {
    rates: Option<HashMap<String, f64>>,
}

impl FxService {
    pub fn new() -> Self {
        let http = Client::builder()
            .timeout(Duration::from_secs(5))
            .build()
            .unwrap_or_else(|_| Client::new());
        Self {
            http,
            cache: Arc::new(RwLock::new(None)),
        }
    }

    /// Converts `amount` from `from_currency` into `base_currency` (e.g. USD -> VND).
    /// Returns `amount` unchanged if currencies match or rate is unavailable.
    pub async fn convert_to_base(
        &self,
        amount: Decimal,
        from_currency: &str,
        base_currency: &str,
    ) -> Decimal {
        let from = from_currency.trim().to_uppercase();
        let base = base_currency.trim().to_uppercase();
        if from.is_empty() || base.is_empty() || from == base {
            return amount;
        }

        let rates = self.get_rates().await;
        let (Some(&from_rate), Some(&base_rate)) = (rates.get(&from), rates.get(&base)) else {
            return amount;
        };

        if from_rate.is_zero() {
            return amount;
        }

        // Convert via USD: amount_in_base = amount / from_rate * base_rate
        let converted = (amount / from_rate) * base_rate;
        if base == "VND" || base == "JPY" || base == "KRW" {
            converted.round_dp(0)
        } else {
            converted.round_dp(2)
        }
    }

    pub async fn get_rates(&self) -> HashMap<String, Decimal> {
        {
            let guard = self.cache.read().await;
            if let Some((fetched_at, ref map)) = *guard {
                if fetched_at.elapsed() < CACHE_TTL {
                    return map.clone();
                }
            }
        }

        let fetched = self.fetch_live_rates().await;
        let rates = fetched.unwrap_or_else(fallback_rates);

        let mut guard = self.cache.write().await;
        *guard = Some((Instant::now(), rates.clone()));
        rates
    }

    async fn fetch_live_rates(&self) -> Option<HashMap<String, Decimal>> {
        let resp = self
            .http
            .get("https://open.er-api.com/v6/latest/USD")
            .send()
            .await
            .ok()?;

        if !resp.status().is_success() {
            return None;
        }

        let body: OpenErResponse = resp.json().await.ok()?;
        let raw_rates = body.rates?;
        let mut map = fallback_rates();
        for (code, val) in raw_rates {
            if let Some(d) = Decimal::from_f64_retain(val) {
                if d > Decimal::ZERO {
                    map.insert(code.to_uppercase(), d);
                }
            }
        }
        Some(map)
    }
}

fn fallback_rates() -> HashMap<String, Decimal> {
    let pairs = [
        ("USD", "1"),
        ("VND", "25400"),
        ("EUR", "0.92"),
        ("GBP", "0.79"),
        ("JPY", "155"),
        ("SGD", "1.34"),
        ("AUD", "1.53"),
        ("CAD", "1.39"),
        ("KRW", "1390"),
        ("THB", "34.5"),
    ];
    pairs
        .into_iter()
        .map(|(k, v)| (k.to_string(), Decimal::from_str(v).unwrap()))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn converts_same_currency_without_change() {
        let fx = FxService::new();
        let amt = Decimal::from_str("50000").unwrap();
        assert_eq!(fx.convert_to_base(amt, "VND", "VND").await, amt);
    }

    #[test]
    fn fallback_rates_include_usd_and_vnd() {
        let r = fallback_rates();
        assert!(r.contains_key("USD"));
        assert!(r.contains_key("VND"));
    }
}
