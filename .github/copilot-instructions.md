# Copilot Instructions — Live Scoring System

## Build & Run

### Backend (Java / Spring Boot)

```bash
# Full build (includes Angular frontend by default)
./gradlew build

# Run development server (builds frontend first)
./gradlew bootRun

# Skip frontend build (use pre-built dist or static assets)
./gradlew bootRun -PskipWebuiBuild=true

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "org.thingai.app.SomeTest"

# Build distribution ZIPs
./gradlew packageWindows   # Windows ZIP with bundled JRE
./gradlew packageMac       # macOS ZIP with bundled JRE
./gradlew packageLinux     # Linux ZIP with bundled JRE
./gradlew distZip          # Generic ZIP (no bundled JRE)
```

### Frontend (Angular)

```bash
cd webui
npm install
npm start          # Dev server at localhost:4200
npm run build      # Production build → webui/dist/webui/browser/
ng test            # Run frontend unit tests (Karma/Jasmine)
```

The Gradle build auto-downloads Node 24.9.0 via the `com.github.node-gradle.node` plugin, so a local Node install is not
required for full builds.

## Architecture

This is a **Spring Boot + Angular** monorepo. The Angular app is compiled into `webui/dist/webui/browser/` and copied
into `build/resources/main/static/` so it is served directly by Spring Boot.

Two JARs are produced:

- `scoring-system.jar` — Spring Boot fat JAR (REST API + WebSocket + embedded Angular)
- `scoring-launcher.jar` — Swing desktop launcher (`DesktopLauncher`) wrapping the server

### Backend layers (`src/main/java/org/thingai/app/`)

| Layer        | Package                         | Responsibility                                                                                                                                                                            |
|--------------|---------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Entry point  | `Main.java`                     | Bootstraps Spring, wires `ScoringService`, registers event-specific classes                                                                                                               |
| Service      | `scoringservice/ScoringService` | Central service; owns all handlers and SQLite DAO; bridged to Spring via `SimpMessagingTemplate`                                                                                          |
| Handlers     | `scoringservice/handler/`       | Business logic: `ScoringHandler`, `RankingHandler`, `MatchControlHandler`, `MatchTimerHandler`, `BroadcastHandler`, `EventHandler`, `ScheduleHandler`, `MatchMakerHandler`, `AuthHandler` |
| Controllers  | `controller/`                   | Thin REST controllers delegating to `ScoringService.handler()` static accessors                                                                                                           |
| Entities     | `scoringservice/entity/`        | Domain model: `Score`, `Match`, `Team`, `Event`, `RankingEntry`, etc.                                                                                                                     |
| Repositories | `scoringservice/repository/`    | Thin DAO wrappers over the custom `applicationbase.jar` `Dao`/`BaseDao` API                                                                                                               |
| DTOs         | `scoringservice/dto/`           | JSON transfer objects for API and WebSocket messages                                                                                                                                      |

**Storage**: SQLite via `DaoSqlite` from `libs/desktopplatform.jar`. Per-event data lives in a separate per-event SQLite
DB; raw match score JSON is stored in a `files/` directory alongside it. The system-level DB is at `scoring_system.db`.

**Real-time updates**: WebSocket via STOMP. All pushes go through
`BroadcastHandler.broadcast(topic, payload, messageType)`, which wraps the payload in `BroadcastMessageDto`. The STOMP
endpoint is `/ws`. Topics follow the pattern `/topic/live/...`.

**Two-phase score commit**: Live scoring during a match is held in memory (transmitted via WebSocket). Scores are only
persisted to the database when committed by the scorekeeper. The frontend must call both submit and commit.

### Plugin pattern for event-specific logic

Each competition season/event implements two interfaces and registers them at startup in `Main.java`:

```java
// 1. Implement IScoreConfig (and extend Score) for scoring rules
public class FanrocScore extends Score implements IScoreConfig { ...
}

// 2. Implement IRankingStrategy for ranking/sorting rules
public class FanrocRankingStrategy implements IRankingStrategy { ...
}

// 3. Register in Main.java
scoringService.

registerScoreClass(FanrocScore .class);
scoringService.

registerRankingStrategy(new FanrocRankingStrategy());
```

A `demo/` package (`DemoScore`, `DefaultRankingStrategy`) provides reference implementations.

### Frontend (`webui/src/app/`)

| Directory            | Purpose                                                                                                                                                       |
|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `core/services/`     | HTTP and WebSocket API services                                                                                                                               |
| `core/models/`       | TypeScript interfaces mirroring backend DTOs                                                                                                                  |
| `core/interceptors/` | HTTP interceptors (auth, error handling)                                                                                                                      |
| `features/`          | Feature modules: `match-control`, `referee`, `scoring-display`, `event-dashboard`, `schedule`, `rankings`, `match-results`, `up-next-display`, `auth`, `home` |

Angular components use `signal()` for reactive state. WebSocket communication uses `@stomp/stompjs`.

## Key Conventions

- **Event-specific packages** live at the top level alongside `scoringservice/`: e.g., `org.thingai.app.fanroc`. Do not
  put them inside `scoringservice/`.
- **Controllers are thin**: no business logic; delegate entirely to the relevant handler via
  `ScoringService.scoringHandler()`, `ScoringService.rankingHandler()`, etc.
- **`RequestCallback<T>`** is the async callback pattern used across handlers (from `applicationbase.jar`).
- **`ILog`** (from `applicationbase.jar`) is used for all logging — not SLF4J/Logback directly. Logging is disabled by
  default in `application.properties` (`logging_level=OFF`).
- **Prettier** is configured for the Angular project: 100 char print width, single quotes, Angular HTML parser.
- The `desktop` source set (`src/app/java`) is separate from `main` and only produces `scoring-launcher.jar`.
- Firefox is explicitly **not supported**; the web UI requires Chrome.
