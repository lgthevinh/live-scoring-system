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

- Backend: Java + Spring Boot (REST + WebSocket STOMP), entry point `org.thingai.app.Main`.
- Frontend: Angular web UI (`webui`) built into Spring Boot static resources.
- Desktop launcher: Swing-based launcher packaged as `scoring-launcher.jar` using FlatLaf.
- Data: Local SQLite databases and JSON file storage; no external services required.

## Core Components

- ScoringService: initializes databases, registers scoring/ranking strategy, and exposes handlers.
- Handlers: auth, event, team, schedule, scoring, ranking.
- Match control: match state transitions and timer control.
- Event-specific logic: scoring and ranking strategies in `src/main/java/eventimpl/` (e.g., Fanroc).

## Data Storage

- System database: `scoring_system.db` (events, auth data, account roles, metadata).
- Event database: `{EVENT_CODE}.db` (teams, matches, scores, ranking entries).
- Event file store: `files/{EVENT_CODE}/` (match JSON exports and assets).
- Match schedule input/output: `binary/MatchMaker*` and `data/match_schedule.txt` for schedule generation.

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
    - Defined endpoints exist but are currently stubbed for future implementation.

## WebSocket

- STOMP endpoint: `/ws`
- Broker destination: `/topic`
- App destination prefix: `/app`
- Used for broadcasting live score updates and match state changes.

## Runtime Configuration

- Default host binding: `0.0.0.0`
- Default port: `8080`
- Logging: disabled by default (`logging_level=OFF`)
- Actuator endpoints: enabled for all web exposure

## Build and Distribution

- Backend build: `./gradlew bootJar` (outputs `scoring-system.jar`).
- Desktop launcher build: `./gradlew desktopJar` (outputs `scoring-launcher.jar`).
- Web UI build: `./gradlew npmBuild` (Angular build in `webui`).
- Distribution zips: `distZip`, `windowsDistZip`, `macDistZip`, `linuxDistZip`.
- Run scripts: `scripts/run.sh`, `scripts/run.bat`, `scripts/run-desktop.sh`, `scripts/run-desktop.bat`.

## Hardware and Deployment Notes

- Designed for Windows and macOS; requires Chrome for best compatibility.
- Runs well on LAN-only setups with a dedicated router/switch for reliability.
- Recommended setup includes a scoring server laptop, field display screens, and referee tablets.
