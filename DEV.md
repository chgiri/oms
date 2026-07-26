# Useful for Developers running locally

### Docker commands
For standard services (Postgres already running locally)
```bash
docker compose up -d kafka redis grafana vault vault-init
```

For gateway
```bash
docker compose up --no-deps --build -d gateway
```

Make sure .env file has following set:
OMS_JWKS_URI=http://localhost:8080/.well-known/jwks.json
OMS_MONOLITH_URI=http://localhost:8080

### Windows CMD
for /f "usebackq tokens=1,* delims==" %A in (`findstr /v /r "^#" .env`) do set "%A=%B"