# SMPStats – AI Coding Instructions

These rules apply to any AI assistant (Copilot, ChatGPT, etc.) working in this repository.

---

## 1. Project Basics

* **Type**: Paper 1.21.x Minecraft plugin (stats, moments, analytics)
* **Language**: Java 21
* **Build**: Maven
* **Storage**: SQLite via `sqlite-jdbc`
* **Tests**: JUnit 5 + MockBukkit

Build command:

```bash
mvn clean package
```

Output JAR: `target/SMPStats.jar`
Paper API is `provided`, SQLite + Gson are shaded.

---

## 2. Architecture & Patterns

### Core

* Entry point: `de.nurrobin.smpstats.SMPStats`

  * Handles `onEnable` / `onDisable`
  * Wires services, listeners, commands, API

* Services (e.g. `StatsService`, `MomentService`, `HeatmapService`):

  * Contain business logic
  * No direct Bukkit logic inside services
  * Use constructor injection (no field injection, no service locators)

* Storage: `de.nurrobin.smpstats.database.StatsStorage`

  * Owns SQLite connection
  * Owns schema + migrations
  * All DB access goes through `StatsStorage`

* Config: `de.nurrobin.smpstats.Settings`

  * Wraps access to `config.yml`
  * **Never** use `FileConfiguration` directly in business logic
  * Add new config fields via `Settings` and `SMPStats.loadSettings()`

### Event Handling

* Listeners live in `de.nurrobin.smpstats.listeners`
* Listeners should be thin:

  * Capture Bukkit event
  * Delegate to relevant service method
* Register all listeners in `SMPStats.registerListeners()`

### HTTP API

* HTTP server: `de.nurrobin.smpstats.api.ApiServer`
* Endpoints: implemented as handlers and registered in `ApiServer.start()`
* Auth: `X-API-Key` header checked in `authorize()`
* New endpoint:

  1. Create a handler class
  2. Register it in `ApiServer`

### Moments Engine

* Config: defined in `config.yml` and parsed by `MomentConfigParser`
* Trigger types: `MomentDefinition.TriggerType`
* New trigger:

  1. Add enum value
  2. Extend parsing/validation
  3. Handle it in `MomentListener`

---

## 3. Branch & Git Workflow (AI MUST follow)

1. **Never work directly on `main` or `dev`.**
2. If current branch is `main`, `dev`, or anything not feature-like:

   * Create and switch to a new branch:

     * Features: `feature/<short-description>`
     * Fixes: `fix/<short-description>`
     * Chores: `chore/<short-description>`
     * Docs: `docs/<short-description>`

Examples:

* `feature/add-heatmap-aggregation`
* `fix/moment-trigger-nullpointer`

Commits should be **small and focused** (one concern per commit).

---

## 4. Testing & Coverage (≥ 80% required)

### General Rules

* Every behavior change or new feature **must** have tests.
* Tests must be meaningful:

  * No placeholder tests
  * No `assertTrue(true)`
  * No tests that just satisfy coverage without checking behavior

Use tests to **debug**:

1. Reproduce a bug with a failing test
2. Fix the code
3. Ensure the test turns green and stays

### MockBukkit Pattern

Use MockBukkit for anything touching Bukkit APIs:

```java
private ServerMock server;
private SMPStats plugin;

@BeforeEach
void setUp() {
    server = MockBukkit.mock();
    plugin = MockBukkit.load(SMPStats.class);
}

@AfterEach
void tearDown() {
    MockBukkit.unmock();
}
```

* Plugin lifecycle, listeners, events → test via MockBukkit
* Services → can be tested as plain unit tests, no Bukkit needed

### Coverage Requirement

* Target: **≥ 80% line coverage** for the project
* After changes:

  * Run tests (e.g. `mvn test`)
  * Check coverage (Jacoco report)
* If coverage < 80%:

  * Add or improve tests for uncovered logic
  * Only then commit / open PR

---

## 5. Database & Migrations

### Schema & Migrations

* Schema managed in `StatsStorage`
* Versioning constant: `SCHEMA_VERSION`
* Migrations implemented in `applyMigrations()` with sequential steps:

  ```java
  if (currentVersion == 1) {
      // migrate 1 -> 2
  }
  ```

**Rules:**

* When changing schema:

  1. Increment `SCHEMA_VERSION`
  2. Add a **new** migration block
* Do **not** change or remove existing migrations (no rewrites)

Tests for migrations are encouraged where feasible.

---

## 6. Configuration Management

* Config version: `SMPStats.CONFIG_VERSION`
* `ensureConfigVersion()` syncs user config with defaults

When adding a new config option:

1. Add the key and default to `src/main/resources/config.yml`
2. Increment `SMPStats.CONFIG_VERSION`
3. Add a corresponding field and getter in `Settings`
4. Update `SMPStats.loadSettings()` to read and pass it

Do not read raw config keys in listeners/services; always use `Settings`.

---

## 7. Coding Conventions

* Logging: use `plugin.getLogger()` – never `System.out` or `printStackTrace()`
* Async:

  * Heavy DB operations or HTTP calls → run asynchronously via the Bukkit scheduler
  * Never block the main server thread
* Design:

  * Keep listeners thin, services focused, and avoid god classes
  * Prefer clear, explicit behavior over clever hacks

---

## 8. Documentation & Changelog

Every change that affects users or behavior must be documented.

* Add a changelog file under `docs/changelog/`:

  * `vX.Y.Z.md` (e.g. `v1.3.5.md`)
* Versioning:

  * Breaking change → bump **major**
  * Backwards-compatible feature → bump **minor**
  * Bugfix / small change → bump **patch**
* Align `pom.xml` version with the changelog version
  (scripts like `set-version.sh` may be available to help with this)

---

## 9. Commit & PR Rules

### Commit Messages

* Prefix with: `feat/`, `fix/`, `docs/`, `chore/`
* Keep title under ~50 characters
* Use dashes in titles, not spaces or colons

Examples:

* `feat/add-heatmap-metrics`
* `fix/fix-nullplayer-in-joinlistener`
* `chore/refactor-moment-service`

### Pull Requests

Before opening a PR:

1. Work is on a feature/fix/chore/docs branch (not `main` or `dev`)
2. All tests pass
3. Coverage ≥ 80%
4. DB migrations (if any) are versioned correctly
5. Config changes (if any) follow the config rules
6. Changelog entry exists for the new version

PR description should:

* Explain **what** changed
* Explain **why** it changed
* Mention relevant tests (what is covered)