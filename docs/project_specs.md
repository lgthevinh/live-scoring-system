# Project Specs - VRC Scoring System

## Overview
The VRC Scoring System is a live scoring platform for robotics competitions. It provides event setup, team management, schedule generation, match control, referee scoring input, live display, and ranking output. The system ships as a Spring Boot backend with an Angular web UI, and supports local event deployments with bundled runtime distributions.

## Goals
- Deliver real-time scoring and display for multi-field robotics matches.
- Provide event setup workflows (teams, schedules, accounts).
- Support season-specific scoring and ranking logic via pluggable strategies.
- Package for offline, on-prem event operation with minimal setup.

## Users and Roles
- Event organizers: configure events, teams, schedules, accounts, and manage match flow.
- Scorekeepers: control match lifecycle (load/activate/start/commit) and overrides.
- Referees: enter alliance scores and submit for review/commit.
- Spectators/teams: view live match displays and rankings.

## System Architecture
- Backend: Java 17, Spring Boot 3.5, SQLite, WebSocket (STOMP), Actuator.
- Frontend: Angular 20, Bootstrap, STOMP client.
- Storage: Local SQLite files for system and per-event data, plus JSON file storage.
- Distribution: Gradle builds server JAR and desktop launcher, bundles platform JREs.

## Backend Components
- `org.thingai.app.Main`: bootstraps Spring, initializes `ScoringService`, registers `FanrocScore` and `FanrocRankingStrategy`.
- `ScoringService`: wires handlers (auth, event, team, schedule, scoring, ranking) and match control.
- Handlers:
  - `EventHandler`: create/list/update/delete events; manage current active event.
  - `TeamHandler`: CRUD and CSV/JSON import.
  - `ScheduleHandler`: generates qualification schedules via external MatchMakerService; creates matches, alliances, and scores.
  - `ScoringHandler`: score definition access and score persistence (DB + JSON file).
  - `RankingHandler`: updates ranking entries based on completed matches.
  - `AuthHandler`: account creation, token auth, and role management.
- Match control:
  - `StateManager`: current/loaded match state and score cache.
  - `MatchControl`: load/activate/start/abort/commit flow, plus timer (180s default).

## Frontend Components
- Routes (key features):
  - `Home`, `Auth`, `Event Dashboard` (accounts, teams, schedule), `Schedule` (qual/playoff), `Match Control`, `Referee` (red/blue + score tracking), `Scoring Display`, `Match Results`, `Rankings`.
- Services:
  - `AuthService`: `/api/auth/*` login and account management.
  - `MatchService`: `/api/match/*` and `/api/score/*` (client expects endpoints).
  - `ScorekeeperService`: `/api/scorekeeper/*` (match control).
  - `RefereeService`: `/api/ref/*` (score submission and UI definitions).
  - `RankService`: `/api/rank/*` (ranking status and recalculation).
  - `BroadcastService`: STOMP over `/ws`, subscribes to topics and publishes updates.

## Data Model and Storage
- System database: `scoring_system.db` in app directory.
  - Entities: `Event`, `AuthData`, `AccountRole`, `DbMapEntity`.
- Event database: `{eventCode}.db` (initialized when event is activated).
  - Entities: `Match`, `AllianceTeam`, `Team`, `Score`, `RankingEntry`, `DbMapEntity`.
- File storage: `files/{eventCode}/` stores JSON snapshots per alliance (e.g., `Q1_R.json`).

## Scoring and Ranking
- Default scoring implementation: `FanrocScore`.
  - Inputs include ball scoring, barrier actions, parking, penalties, and card status.
  - Balancing coefficient depends on ball imbalance and barrier condition; red card zeroes score.
- Default ranking strategy: `FanrocRankingStrategy`.
  - Win/tie/loss points: 3/2/1.
  - Sort order: total score (desc), then highest match score (desc).

## Match Lifecycle
- States (`MatchState`): NOT_STARTED -> LOADED -> ACTIVE -> IN_PROGRESS -> ON_REVIEW -> SUBMITTED -> COMPLETED.
- Score states (`ScoreState`): NOT_SCORED -> ON_REVIEW -> SCORED.
- Qualification schedule generation:
  - Uses external binary in `binary/` via `MatchMakerService`.
  - Outputs to `data/match_schedule.txt` and parses a 2v2 alliance schedule.
  - Teams are shuffled; schedule indices map to shuffled list (1-based).
  - Supports time blocks (breaks) and multi-field assignment.

## API Surface

### Implemented Endpoints
- Auth: `/api/auth/*`
  - `POST /api/auth/login`
  - `POST /api/auth/refresh`
  - `GET /api/auth/local-ip`
  - `POST /api/auth/create-account`
  - `GET /api/auth/users`
  - `GET /api/auth/accounts`
  - `PUT /api/auth/accounts/{username}`
  - `DELETE /api/auth/accounts/{username}`
- Teams (v1): `/api/v1/team/*` (create/list/update/delete)
- Teams (v2): `/api/v2/teams` (CRUD + `POST /import` for CSV or JSON)

### Planned/Stubbed Endpoints
Controllers exist but methods are empty or not yet wired:
- `/api/match/*`, `/api/scores/*`, `/api/rank/*`, `/api/event/*`, `/api/sync/*`, `/api/scorekeeper/*`.
- WebSocket message handlers under `/app/live/score/*` exist but are no-op.

### Planned API Contract (Docs)
See `docs/plan/Apis.md` for the target REST and WebSocket contracts, including:
- Match/schedule management, match control, scoring submission, rankings, team management, sync, and live display events.

## WebSocket Messaging
- STOMP endpoint: `/ws`.
- Broker prefix: `/topic`.
- App destination prefix: `/app`.
- Live topics (server constants):
  - `/topic/live/match`, `/topic/live/score/red`, `/topic/live/score/blue`.
  - `/topic/live/display/score`, `/topic/live/display/control`, `/topic/live/display/ranking`.
- Payload format: `BroadcastMessageDto { type, payload }`.

## Security and Auth
- Token format: Base64 of `username:timestamp:secret_key`.
- Token validity: 24 hours.
- Local login: `local` user is allowed from localhost without password.
- CORS: wildcard allowed (see `CorsConfig`).

## Build and Run
- Backend: `./gradlew build`, `./gradlew bootRun`.
- Frontend (standalone): `cd webui && npm install && npm start`.
- Integrated build: Gradle Node plugin downloads Node and runs `npm run build`, then packages static assets into the Spring Boot JAR.
- Server port: `8080` (see `src/main/resources/application.properties`).

## Distribution Packaging
- `bootJar` produces `scoring-system.jar`.
- `desktopJar` produces `scoring-launcher.jar`.
- Distribution zips:
  - `distZip` (generic), `windowsDistZip`, `macDistZip`, `linuxDistZip`.
  - Platform zips bundle JREs under `jre/`.

## Known Gaps / WIP Areas
- Most match, scoring, rank, and match-control API endpoints are declared but not implemented yet.
- WebSocket message handlers exist but do not process inbound updates.
- Frontend currently relies on endpoints that are not fully wired on the backend; it aligns with the API plan in `docs/plan/Apis.md`.

## Key Paths
- Backend entry: `src/main/java/org/thingai/app/Main.java`.
- Backend service core: `src/main/java/org/thingai/app/scoringservice/`.
- Frontend app: `webui/src/app/`.
- API plan: `docs/plan/Apis.md`.
