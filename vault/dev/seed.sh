#!/bin/sh
# Run once by docker-compose's `vault-init` service against the local
# dev-mode `vault` container (see docker-compose.yml). Writes the same five
# secrets that env.example marks required/no-default for prod into
# secret/oms/dev — Spring Cloud Vault's default context resolution means the
# app (running with spring.profiles.active=dev) reads exactly this path
# automatically, no extra config needed on the app side.
#
# This is dev-mode Vault: in-memory, unsealed, single root token. None of
# this is how uat/prod are set up — see vault/README.md for the real KV +
# Kubernetes-auth setup those use instead.
set -eu

vault kv put secret/oms/dev \
  DB_PASSWORD="${DB_PASSWORD}" \
  REDIS_PASSWORD="${REDIS_PASSWORD}" \
  JWT_PRIVATE_KEY="${JWT_PRIVATE_KEY}" \
  JWT_PUBLIC_KEY="${JWT_PUBLIC_KEY}" \
  DEFAULT_ADMIN_PASSWORD="${DEFAULT_ADMIN_PASSWORD}"

echo "vault-init: seeded secret/oms/dev"
