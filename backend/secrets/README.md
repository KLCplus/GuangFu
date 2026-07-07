# Local Secrets Directory

Put local-only secret files here. Files in this directory are ignored by Git.

For QWeather JWT, put the Ed25519 private key here by default:

```text
backend/secrets/ed25519-private.pem
```

Then set this in the project root `.env`:

```env
QWEATHER_PRIVATE_KEY_PATH=.\secrets\ed25519-private.pem
```

Do not commit private keys, JWTs, database passwords, OAuth secrets, mail authorization codes, or cloud access keys.
