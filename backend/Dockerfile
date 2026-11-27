# Stage 1: Build release binary (Debian 12 bookworm matches distroless/cc-debian12)
FROM rust:slim-bookworm AS builder

WORKDIR /app

# Cache dependency compilation separately from application source
COPY Cargo.toml Cargo.lock ./
RUN mkdir src \
    && echo "fn main() {}" > src/main.rs \
    && cargo build --release \
    && rm -rf src

# Copy actual source, embedded web assets, and sqlx migrations
COPY migrations ./migrations
COPY web ./web
COPY src ./src

# Touch main.rs so Cargo recompiles our crate (not dependencies)
RUN touch src/main.rs \
    && cargo build --release \
    && strip target/release/spending-tracker-api

# Stage 2: Minimal distroless runtime (non-root, ~25-35 MB total image)
FROM gcr.io/distroless/cc-debian12:nonroot

WORKDIR /app
COPY --from=builder /app/target/release/spending-tracker-api /app/spending-tracker-api

ENV PORT=8080
ENV RUST_LOG=spending_tracker_api=info,tower_http=info
EXPOSE 8080

ENTRYPOINT ["/app/spending-tracker-api"]
