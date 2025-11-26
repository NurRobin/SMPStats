# SMPStats AI Coding Instructions

## Project Context
SMPStats is a Paper 1.21.x Minecraft plugin for tracking player statistics, moments, and analytics. It features an embedded HTTP API, SQLite storage, and various analytics modules (Heatmaps, Social, Timeline).

- **Language**: Java 21
- **Framework**: Paper API (1.21.x)
- **Build System**: Maven
- **Storage**: SQLite (via `sqlite-jdbc`)
- **Testing**: JUnit 5 + MockBukkit

## Architecture & Patterns

### Core Structure
- **Main Class**: `de.nurrobin.smpstats.SMPStats` is the entry point. It handles lifecycle (`onEnable`, `onDisable`) and manually wires dependencies.
- **Service Layer**: Business logic is encapsulated in service classes (e.g., `StatsService`, `MomentService`, `HeatmapService`).
  - **Pattern**: Services are instantiated in `SMPStats.onEnable()` and passed to Listeners and Commands.
  - **Dependency Injection**: Manual constructor injection. Most services require `SMPStats` (plugin instance), `StatsStorage`, and `Settings`.
- **Data Access**: `de.nurrobin.smpstats.database.StatsStorage` manages the SQLite connection and schema migrations.
- **Configuration**: `de.nurrobin.smpstats.Settings` wraps `config.yml` access. Do not access `FileConfiguration` directly in business logic; add methods to `Settings`.

### Event Handling
- **Listeners**: Located in `de.nurrobin.smpstats.listeners`.
- **Pattern**: Listeners should be lightweight. They capture Bukkit events and delegate processing to the appropriate Service.
- **Registration**: Register new listeners in `SMPStats.registerListeners()`.

### HTTP API
- **Embedded Server**: `de.nurrobin.smpstats.api.ApiServer` handles HTTP requests.
- **Endpoints**: Defined in `ApiServer` or separate handler classes.

## Development Workflow

### Build
- **Command**: `mvn clean package`
- **Output**: `target/SMPStats.jar`
- **Dependencies**: Paper API is `provided`. `sqlite-jdbc` and `gson` are shaded/included.

### Database
- **Schema**: Managed in `StatsStorage`.
- **Migrations**: 
  - Controlled by `SCHEMA_VERSION` constant in `StatsStorage`.
  - Implemented in `applyMigrations()` using sequential version checks (e.g., `if (currentVersion == 1) { ... }`).
  - **Rule**: When modifying the schema, increment `SCHEMA_VERSION` and add a new migration block. Do not modify existing migration blocks.

### Configuration Management
- **Versioning**:
  - `SMPStats.CONFIG_VERSION` tracks the config file version.
  - `ensureConfigVersion()` automatically adds missing keys from the default `config.yml` to the user's file.
  - **Rule**: When adding a new setting:
    1. Add it to `src/main/resources/config.yml`.
    2. Increment `SMPStats.CONFIG_VERSION`.
    3. Add the field to `Settings.java` (and its constructor).
    4. Update `SMPStats.loadSettings()` to read the new value.

### HTTP API
- **Endpoints**: Manually registered in `ApiServer.start()`.
- **Auth**: Handled via `X-API-Key` header check in `authorize()`.
- **Pattern**: Create a new `HttpHandler` implementation and register it in `ApiServer`.

### Moments Engine
- **Definitions**: Configured in `config.yml` (parsed by `MomentConfigParser`).
- **Triggers**: Enum `MomentDefinition.TriggerType`. Adding a new trigger requires updating the enum and handling the event in `MomentListener`.

## Testing Strategy

### MockBukkit Integration
- **Framework**: Use `MockBukkit` to simulate the server environment.
- **Pattern**:
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
- **Scope**: Prefer integration tests using MockBukkit over pure unit tests for logic involving Bukkit APIs (Events, Players, Scheduler).

## Coding Conventions
- **Logging**: Use `plugin.getLogger()` instead of `System.out`.
- **Async**: Use `Bukkit.getScheduler().runTaskAsynchronously` for heavy DB operations or API calls.
- **Nullability**: Assume Bukkit API returns non-null unless specified. Use `Optional` for internal service returns where appropriate.
