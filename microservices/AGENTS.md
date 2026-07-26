# Repository Guidelines

## Project Structure & Module Organization

This is a Java 21, Maven multi-module Spring Boot repository. The root `pom.xml` aggregates seven modules: `wc-common` holds shared infrastructure; `wc-gateway` routes requests and filters JWTs; `wc-user`, `wc-shop`, `wc-community`, `wc-message`, and `wc-ai` contain business services. Each follows Maven layout: code under `src/main/java`, configuration under `src/main/resources/application.yml`, and tests under `src/test/java`. Keep controllers, services, DAOs, domain objects, forms, and VOs in their existing package layers. Never commit `target/` content or IDE metadata.

## Build, Test, and Development Commands

- `mvn clean package` — compile and package every module from the repository root.
- `mvn test` — run all configured tests across the reactor.
- `mvn -pl wc-user -am test` — test one service plus required modules.
- `mvn -pl wc-gateway -am spring-boot:run` — start the gateway and build dependencies as needed; replace the module name to run another service.

Local services expect infrastructure configured in each `application.yml`: Nacos, MySQL, Redis, RabbitMQ, and where relevant Elasticsearch or an AI provider. The gateway uses port 8080; services use 8081–8085.

## Coding Style & Naming Conventions

Use four-space indentation, UTF-8, and standard Java brace placement. Packages are lowercase under `cn.lx.worldcoffee`; classes use PascalCase, methods and fields use camelCase, and constants use `UPPER_SNAKE_CASE`. Preserve suffix conventions such as `Controller`, `Service`, `Dao`, `Form`, and `VO`. Prefer constructor injection (typically Lombok `@RequiredArgsConstructor`), validate request models with Jakarta Validation, and return the shared `Result<T>` envelope. No formatter is enforced, so match nearby code and organize imports before committing.

## Testing Guidelines

No test sources or coverage threshold are currently committed. New behavior should add JUnit 5/Spring Boot tests under `<module>/src/test/java`, mirroring the production package. Name unit tests `*Test` and integration tests `*IntegrationTest`. Cover success, validation, authorization, and failure paths; mock external services for unit tests. Add `spring-boot-starter-test` to the relevant module when introducing its first tests.

## Commit & Pull Request Guidelines

History favors concise Conventional Commit prefixes such as `feat:`, `fix:`, `refactor:`, and `chore:`. Keep each commit focused and use an imperative summary. Pull requests should identify affected modules, describe behavior and configuration changes, link related issues, and report verification commands. Include API examples or screenshots when responses or UI-visible behavior changes.

## Security & Configuration

Do not commit real JWT secrets, database passwords, API keys, or machine-specific upload paths. Use environment variables or local profiles, and document any new required configuration in the pull request.

Treat any command using `rm` as a high-risk action. Before running it, explicitly ask the user for confirmation and explain the target path and expected effect.
