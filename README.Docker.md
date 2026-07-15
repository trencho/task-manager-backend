### Building and running with Docker Compose

This project ships a `docker-compose.yml` that brings up the API together with a MongoDB instance.
Both images are built from the digest-pinned Dockerfiles under [`docker/`](docker/) (`docker/java`,
`docker/mongo`).

Compose reads every required value from the environment and **refuses to start if any is unset**
rather than substituting an empty string — the application itself fails fast on a missing
`JWT_SECRET` or `MONGODB_URI`. So supply an env file first:

```bash
cp .env.example .env.local            # then fill in every blank (see the env-var table in README.md)
docker compose --env-file .env.local up --build
```

The API is then available at `http://localhost:${SERVER_PORT:-80}` and the management endpoints at
`http://localhost:${MANAGEMENT_PORT:-9090}`. `.env.local` is git-ignored; never commit real
credentials.

### Notes

- The full list of variables (which are required, which are compose-only, and their defaults) lives
  in the [main README](README.md#configuration).
- `MONGO_APP_USERNAME` / `MONGO_APP_PASSWORD` must match the credentials in `MONGODB_URI`; the app
  account is created by [`docker/mongo/init/init-mongo.js`](docker/mongo/init/init-mongo.js) on the
  **first** initialization of the data volume only.
