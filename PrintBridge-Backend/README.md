# PrintBridge backend

The Quarkus API of PrintBridge. See the [root README](../README.md) for what the app does and how to start everything, and [docs/architecture.md](../docs/architecture.md) for the packages and layering.

```sh
./gradlew quarkusDev   # dev mode on :8080, needs the Postgres from ../local
./gradlew build        # compile and run all tests (needs Docker for the throwaway Postgres)
```

- JDK 21 or newer.
- Coverage report after the tests: `build/reports/jacoco/test/html/index.html`.
- Configuration: environment variables, see `src/main/resources/application.properties` and `../.env.example`.
- Docker image: `./gradlew quarkusBuild`, then `docker build -f src/main/docker/Dockerfile.jvm -t printbridge-backend .`
