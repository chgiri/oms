# Vault setup

This covers the Vault-*server*-side configuration the app depends on. None
of it lives in this repo's Vault server (there isn't one, outside local dev)
— it has to be run once, by whoever administers your real Vault cluster,
against that cluster.

## What the app reads

Regardless of environment, the app resolves five values from Vault instead
of plain env vars, whenever `spring.cloud.vault.enabled=true`:

- `DB_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_PRIVATE_KEY`
- `JWT_PUBLIC_KEY`
- `DEFAULT_ADMIN_PASSWORD`

These are stored under a KV v2 secrets engine mounted at `secret/`, at
`secret/oms/<profile>` (e.g. `secret/oms/uat`, `secret/oms/prod`) — Spring
Cloud Vault's default context resolution (`spring.application.name` +
active profile) finds this path with no extra config on the app side. See
`application.properties`' Vault block for exactly what's configured there.

## Local dev

Nothing to do — `docker compose up` starts a `vault` container in Vault's
built-in dev mode (in-memory, auto-unsealed, single root token) and
`vault-init` seeds `secret/oms/dev` from your `.env` file via
`vault/dev/seed.sh`. Dev mode is not suitable for anything beyond this.

## uat / prod (Kubernetes auth)

The app authenticates as its own Kubernetes ServiceAccount — no token or
credential file to manage or rotate. This assumes:
- A real Vault cluster reachable from the k8s cluster, with `VAULT_ADDR`
  pointing at it (see `k8s/00-configmap.yaml`).
- The Kubernetes auth method enabled at the default path (`kubernetes`), or
  a custom path matched by `VAULT_KUBERNETES_AUTH_PATH`.

One-time setup per environment (repeat for `uat` and `prod`, substituting
the environment name throughout):

```bash
# 1. Enable the Kubernetes auth method (skip if already enabled).
vault auth enable kubernetes

# 2. Point it at the cluster's API server and CA cert.
vault write auth/kubernetes/config \
  kubernetes_host="https://<k8s-api-server>:443" \
  kubernetes_ca_cert=@/path/to/ca.crt

# 3. A policy scoped to only this environment's secrets — prod's role can
#    never read uat's, or vice versa.
cat <<EOF | vault policy write oms-prod-policy -
path "secret/data/oms/prod" {
  capabilities = ["read"]
}
EOF

# 4. Bind that policy to a role tied to the app's ServiceAccount + namespace
#    (see k8s/12-serviceaccount.yaml for the ServiceAccount name/namespace).
vault write auth/kubernetes/role/oms-app-prod \
  bound_service_account_names=oms-app \
  bound_service_account_namespaces=oms \
  policies=oms-prod-policy \
  ttl=1h

# 5. Write the actual secret values (from wherever your real prod
#    credentials come from — never from this repo).
vault kv put secret/oms/prod \
  DB_PASSWORD="..." \
  REDIS_PASSWORD="..." \
  JWT_PRIVATE_KEY="..." \
  JWT_PUBLIC_KEY="..." \
  DEFAULT_ADMIN_PASSWORD="..."
```

The role name (`oms-app-prod` / `oms-app-uat`) matches
`VAULT_KUBERNETES_ROLE` in `application-{prod,uat}.properties` — change one,
change the other.

## Rotating a secret

Write a new value with `vault kv put` (KV v2 keeps history) and restart the
pods — Spring Cloud Vault reads Vault once at startup, not continuously, so
a rotation only takes effect on the next restart unless you also add
`spring.cloud.vault.config.lifecycle.enabled=true` and lease renewal, which
isn't configured here.
