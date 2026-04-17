# Live Scoring System - Project Specs

## Summary

The Live Scoring System is a web-based scoring platform for robotics competitions. It runs on a local network, captures
match results in real time, and presents live scores, rankings, and match status to referees, organizers, teams, and
spectators.

## Goals

- Provide real-time scoring, match control, and scoreboard display during live events.
- Allow event organizers to configure events, teams, schedules, and scoring rules.
- Support offline or LAN-only deployments for reliability and security.

## Primary Users

- Event organizers (event setup, match scheduling, rankings).
- Scorekeepers/referees (score entry and match control).
- Teams and spectators (live scoreboard display).

## System Architecture

- Backend: Java + Spring Boot 3.5 (REST + WebSocket STOMP), entry point `org.thingai.app.Main`.
- Frontend: Angular 20 web UI (`webui`) built into Spring Boot static resources.
- Desktop launcher: Swing-based launcher packaged as `scoring-launcher.jar` using FlatLaf.
- Data: Local SQLite databases (via HikariCP + sqlite-jdbc) and JSON file storage; no external services required.

## Repository Layout

Top-level directories:

- `src/main/java/` – Java backend source (packages: `org.thingai.app.*`, `eventimpl.*`).
- `src/main/resources/` – Spring configuration (`application.properties`).
- `src/test/` – JUnit 5 test tree (currently minimal; `src/test/resources/script/websocket_tester.html` is a manual WS tester).
- `webui/` – Angular 20 frontend (`src/app/`, `angular.json`, `package.json`).
- `scripts/` – Runtime launchers: `run.sh`, `run.bat`, `run-desktop.sh`, `run-desktop.bat`.
- `config/` – External build configs (e.g. `launch4j-config-windows.xml`).
- `data/` – Seed inputs (e.g. `match_schedule.txt` for MatchMaker).
- `binary/` – Platform-specific MatchMaker executables (`MatchMaker`, `MatchMaker.exe`, `MatchMaker_mac.dmg`).
- `files/` – Runtime file storage; per-event subdirs `files/{EVENT_CODE}/` hold match JSON exports.
- `libs/` – Vendored internal jars (`applicationbase.jar`, `edgeplatform.jar`).
- `jre/` – Optional bundled JREs for distribution zips.
- `docs/` – Documentation (`docs/specs/`, `docs/image/`).
- `build/` – Gradle outputs (`scoring-system.jar`, `scoring-launcher.jar`, dist zips).

Root README files: `README.md` (Chinese), `README-en.md` (English).

## Core Components

- `org.thingai.app.Main` – Spring Boot entry point; registers Fanroc scoring/ranking strategies and starts `ScoringService`.
- `org.thingai.app.scoringservice.ScoringService` – initializes databases, wires handlers, registers strategies.
- Handlers (`org.thingai.app.scoringservice.handler.*`): `AuthHandler`, `EventHandler`, `TeamHandler`, `MatchHandler`,
  `ScoreHandler`, `ScheduleHandler`, `RankingHandler`.
- Match control (`org.thingai.app.scoringservice.matchcontrol.*`): `StateManager`, `MatchControl`, `ScoreControl`.
- Services (`org.thingai.app.scoringservice.service.*`): `MatchMakerService` (schedule generation),
  `MatchTimerService` (countdown), `BroadcastService` (WebSocket publishing).
- Repositories (`org.thingai.app.scoringservice.repository.*`): `LocalRepository` plus `Dao*` classes for each entity.
- Entities (`org.thingai.app.scoringservice.entity.*`): `Event`, `Match`, `Team`, `AllianceTeam`, `Score`,
  `RankingEntry`, `RankingSnapshot`, `AuthData`, `AccountRole`.
- Defines/enums (`org.thingai.app.scoringservice.define.*`): `MatchState`, `AllianceColor`, `AccountRole`,
  `ScoreState`, `ErrorCode`, `DisplayControlAction`, `LiveBroadcastTopic`.
- Strategies (`org.thingai.app.scoringservice.strategy.*`): `IScoreStrategy`, `IRankingStrategy` – pluggable per-season.
- REST controllers (`org.thingai.app.api.endpoints.*`): `AuthApi`, `TeamApi`, `MatchApi`, `MatchControlApi`, `EventApi`,
  `ScoreApi`, `RankApi`.
- Desktop launcher (`org.thingai.app.desktop.DesktopLauncher`): Swing UI using FlatLaf.
- Event-specific logic (`eventimpl/*`): `eventimpl/fanroc/FanrocScore` + `FanrocRankingStrategy` (current season),
  `eventimpl/demo/*` reference implementations.

## Frontend Layout (`webui/src/app/`)

- `app.ts`, `app.routes.ts`, `app.config.ts` – standalone Angular root + routes + providers.
- `core/services/` – `AuthService`, `EventService`, `TeamService`, `ScoreService`, `RankService`,
  `MatchControlService`, `BroadcastService`, `BroadcastEventsService`.
- `core/models/` – TS interfaces mirroring backend entities (`Event`, `Match`, `Score`, `Team`, `Rank`, `Broadcast`).
- `core/interceptors/auth.interceptors.ts` – attaches auth token to outgoing requests.
- `core/define/` – constants (`AccountRoleType`, `FieldDisplayCommand`).
- `features/` – feature modules: `auth`, `event-dashboard`, `referee`, `match-control`, `rankings`, `schedule`,
  `match-results`, `scoring-display`, `up-next-display`.
- Tech: Angular 20, Bootstrap 5, RxJS 7.8, `@stomp/stompjs` for WebSocket.

## Data Storage

- System database: `scoring_system.db` (events, auth data, account roles, metadata).
- Event database: `{EVENT_CODE}.db` (teams, matches, scores, ranking entries). One DB per event.
- Event file store: `files/{EVENT_CODE}/` (match JSON exports and assets).
- Match schedule input/output: `binary/MatchMaker*` executable consumes/produces `data/match_schedule.txt`.
- Databases live in the app working directory (resolved by the internal `org.thingai.base.Service` helper).

## API Surface

Base paths and notable endpoints:

- Auth (`/api/auth`)
    - `POST /login` (supports local user login from localhost)
    - `POST /refresh`
    - `POST /create-account`
    - `GET /users`
    - `GET /accounts`
    - `PUT /accounts/{username}`
    - `DELETE /accounts/{username}`
    - `GET /local-ip`

- Team V1 (`/api/v1/team`)
    - `POST /create`
    - `GET /list`
    - `PUT /update`
    - `DELETE /delete/{id}`

- Team V2 (`/api/v2/teams`)
    - `POST /` (create)
    - `GET /` (list)
    - `GET /{id}`
    - `PUT /{id}`
    - `DELETE /{id}`
    - `POST /import` (JSON array, JSON object, or CSV payload)

- Match (`/api/match`), Scores (`/api/scores`), Rank (`/api/rank`), Sync (`/api/sync`), Scorekeeper (
  `/api/scorekeeper`), Event (`/api/event`)
    - Defined endpoints exist; some are stubbed pending implementation. Check the corresponding `*Api.java` before
      adding new routes.

## WebSocket

- STOMP endpoint: `/ws`
- Broker destination prefix: `/topic`
- App destination prefix: `/app`
- Used for broadcasting live score updates and match state changes.
- Topic names are centralized in `LiveBroadcastTopic` (backend) and mirrored by the frontend `BroadcastService`.

## Runtime Configuration

`src/main/resources/application.properties`:

- Default host binding: `0.0.0.0`
- Default port: `8080`
- Logging: disabled by default (`logging_level=OFF`); raise to `DEBUG` for troubleshooting.
- Actuator endpoints: enabled for all web exposure.

## Build and Distribution

Key Gradle tasks (see `build.gradle`):

- `./gradlew bootJar` – builds `build/libs/scoring-system.jar` (web service; embedded webui).
- `./gradlew desktopJar` – builds `build/libs/scoring-launcher.jar` (Swing launcher).
- `./gradlew bootRun` – runs Spring Boot in dev.
- `./gradlew npmInstall` / `npmBuild` – installs and builds the Angular app (Node managed via Gradle Node plugin).
- `./gradlew copyWebUi` / `copyWebUiIfPresent` – copies `webui/dist/webui/browser` into
  `build/resources/main/static` so `bootJar` serves it.
- `./gradlew distZip`, `windowsDistZip`, `macDistZip`, `linuxDistZip` – platform distribution zips (with bundled JRE).
- `./gradlew packageWindows | packageMac | packageLinux` – aliases for platform zips.
- `./gradlew proguard` – obfuscation pass.
- Skip frontend rebuild: `./gradlew bootJar -PskipWebuiBuild=true` (uses existing `webui/dist`).

Run scripts live in `scripts/`.

## Hardware and Deployment Notes

- Designed for Windows and macOS; requires Chrome for best compatibility.
- Runs well on LAN-only setups with a dedicated router/switch for reliability.
- Recommended setup includes a scoring server laptop, field display screens, and referee tablets.

---

## AI Agent Usage Guide

This section is written for AI coding agents (Claude Code, Cursor, etc.) working in this repo. Read before editing.

### Orientation Checklist

Before making changes, an agent should:

1. Read this file (`docs/specs/project-specs.md`) in full.
2. Read `README-en.md` for user-facing behavior.
3. Skim `org.thingai.app.Main`, `ScoringService`, and the handler for the domain being touched.
4. For scoring/ranking changes, read `eventimpl/fanroc/FanrocScore.java` and `FanrocRankingStrategy.java`.
5. For frontend changes, read `webui/src/app/app.routes.ts` and the relevant `features/*` module.

### Where to Make Common Changes

| Task | Primary files |
|------|---------------|
| Add/modify scoring rule | `src/main/java/eventimpl/fanroc/FanrocScore.java` (`calculateTotalScore`, `getBalancingCoefficient`) |
| Change ranking logic | `src/main/java/eventimpl/fanroc/FanrocRankingStrategy.java` |
| Add a new season | Create `src/main/java/eventimpl/{season}/{Season}Score.java` + `{Season}RankingStrategy.java`, register in `Main.java` |
| New REST endpoint | Add method in `org.thingai.app.api.endpoints.{Domain}Api`; delegate to a handler via `ScoringService.{handler}()` |
| New domain logic | Extend the matching `*Handler`; keep controllers thin |
| Schema / persistence | Update entity in `scoringservice/entity/`, DAO in `scoringservice/repository/Dao*.java`, and `LocalRepository` init |
| New WebSocket topic | Add constant to `LiveBroadcastTopic`; publish via `BroadcastService`; subscribe from Angular `BroadcastService` |
| New error code | Add to `scoringservice/define/ErrorCode` |
| New Angular route/page | Add route in `webui/src/app/app.routes.ts`, component under `webui/src/app/features/{feature}/`, service under `webui/src/app/core/services/` |
| New API client | Add service in `webui/src/app/core/services/`, model in `core/models/` |
| Build/dependency change | `build.gradle` (backend + Node plugin); `webui/package.json` (frontend) |
| Runtime config | `src/main/resources/application.properties` |

### Conventions to Follow

- **Logging**: use `ILog.i/w/e` (custom SLF4J wrapper initialized in `ScoringService`). Do not add `System.out.println`.
- **Package structure**: backend stays under `org.thingai.app.*`; event-specific strategies live under flat
  `eventimpl/{season}` packages.
- **Layering**: Controller (`api/endpoints`) → Handler (`scoringservice/handler`) → Repository (`scoringservice/repository`).
  Controllers should not touch DAOs directly.
- **DTOs vs entities**: expose `dto/*` on the API boundary; keep `entity/*` internal.
- **Enums first**: prefer adding to `define/*` enums (`MatchState`, `AccountRole`, `ErrorCode`, `LiveBroadcastTopic`,
  etc.) rather than string literals.
- **Error handling**: return `ErrorCode` values; avoid throwing raw exceptions across API boundaries.
- **Frontend state**: singleton services in `core/services`; feature components stay dumb where possible.
- **STOMP**: subscribe/publish only through `BroadcastService`; never instantiate a raw STOMP client elsewhere.
- **Formatting**: match existing indentation (4 spaces Java, 2 spaces TS/HTML). Do not reformat unrelated lines.

### Do / Don't

- Do update both `FanrocScore` and its DTO/Angular model when adding a score field – the field must flow all the way
  to `scoring-display`.
- Do keep the webui build reproducible: run `./gradlew npmBuild` after frontend changes before `bootJar`.
- Do write new tests under `src/test/java` using JUnit 5 when touching handler/strategy logic.
- Don't introduce external services (no cloud DB, no message brokers) – offline/LAN operation is a hard requirement.
- Don't commit generated artifacts (`build/`, `webui/dist/`, `*.db` produced at runtime).
- Don't rename packages/classes that appear in `Main.java` registration or `application.properties` without updating
  all references.
- Don't assume logging is on – enable via `logging_level=DEBUG` locally; never leave it on in committed config.

### Verification Before Finishing

After a change, an agent should (at minimum):

1. `./gradlew bootJar` – backend compiles and packages cleanly.
2. For webui changes: `./gradlew npmBuild` – Angular builds with no errors.
3. Run relevant JUnit tests if any exist (`./gradlew test`).
4. Manually confirm touched endpoints via `src/test/resources/script/websocket_tester.html` or a REST client when
   feasible.

### Safe Exploration Commands

- List backend packages: `rg --files src/main/java | head`
- Find a handler: `rg -l "class .*Handler" src/main/java`
- Find a STOMP topic: `rg "LiveBroadcastTopic" src webui/src`
- Find an Angular service: `rg -l "@Injectable" webui/src/app/core/services`

### Open Items / Known Gaps

- `/api/match`, `/api/scores`, `/api/rank`, `/api/sync`, `/api/scorekeeper`, `/api/event` contain stubbed endpoints –
  confirm implementation status in the relevant `*Api.java` before relying on them.
- No dedicated `CLAUDE.md` or `.cursorrules` file exists; this section is the canonical agent guidance. Update it
  whenever conventions or layout change.
