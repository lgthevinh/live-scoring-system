# Feature Plan - Match Control

## Goal
Implement end-to-end match control so scorekeepers can load, activate, start, abort, commit, and override matches while keeping state consistent across backend, WebSocket broadcasts, and UI.

## Scope
- Backend REST endpoints under a new match-control namespace.
- Match lifecycle state management and timer integration.
- WebSocket broadcasts for match state, live scores, and display control.
- Frontend wiring will be handled in the rewrite (not constrained by current services).

## Non-Goals
- Redesign of UI or scoring rules.
- Full sync integration with external systems.

## Current Gaps
- `MatchControlApi` is empty.
- `MatchControl` and `StateManager` have placeholders (override, display control callbacks).
- WebSocket message handlers exist but do not process inbound updates.
- No stable match-control API contract is defined yet.

## API Contracts (Proposed)
Define a backend-first API under `/api/match-control` (independent of legacy UI):
- `GET /api/match-control/state`
  - Returns: `{ loadedMatchId, currentMatchId, state, timerSecondsRemaining? }`
- `POST /api/match-control/load`
  - Body: `{ matchId }`
- `POST /api/match-control/activate`
  - Body: `{ matchId? }` (defaults to loaded match if omitted)
- `POST /api/match-control/start`
  - Body: `{}`
- `POST /api/match-control/abort`
  - Body: `{}`
- `POST /api/match-control/commit`
  - Body: `{}`
- `POST /api/match-control/override`
  - Body: `{ allianceId, scoreData, penaltiesScore?, totalScore? }`
- `POST /api/match-control/display`
  - Body: `{ action, data? }` where `action` maps to `DisplayControlAction`

## Data and State
- Use `StateManager` to track:
  - `loadedMatchId` (next match queued)
  - `currentMatchId` (active match)
  - `currentMatchState` (`MatchState`)
- Ensure score state transitions (`ScoreState`) are enforced when committing or overriding.
- Match timer: use `MatchTimerService` to emit timer updates and end-of-match events.

## Broadcasts and Topics
- STOMP endpoint `/ws`, app prefix `/app`, broker `/topic`.
- Use `BroadcastService.broadcast` to send `BroadcastMessageDto` with `type` and `payload`.
- Topics:
  - `/topic/live/match` for lifecycle and active match state.
  - `/topic/live/score/red` and `/topic/live/score/blue` for score updates.
  - `/topic/live/display/control` for display actions (`DisplayControlAction`).

## Implementation Steps

### 1) Backend: MatchControlApi endpoints
- Implement methods to call `ScoringService.matchControl()` and `ScoringService.stateManager()`.
- Validate requested match ID exists via `LocalRepository.matchDao()`.
- Update current/loaded match IDs and state transitions.
- Return consistent JSON responses `{ message, matchId?, state? }`.

### 2) Backend: MatchControl state transitions
- `loadMatch(matchId)` -> set `loadedMatchId` (and optionally `currentMatchId` if empty) and state `LOADED`.
- `activeMatch(matchId)` -> set `currentMatchId` and state `ACTIVE`.
- `startMatch()` -> set state `IN_PROGRESS`, start timer.
- `abortMatch()` -> set state `ACTIVE`, stop/reset timer.
- `commitScore()` -> set state `COMPLETED`, finalize scores, update ranking.
- `overrideScore()` -> allow score overwrite when in `ON_REVIEW` or `SUBMITTED` (guarded).

### 3) Backend: Score commit workflow
- Confirm both alliances have `ScoreState.SCORED` before commit.
- Persist scores via `ScoringHandler.updateScore`.
- Call `RankingHandler.updateRanking(matchId)` after commit.
- Broadcast final score and ranking update.

### 4) Backend: WebSocket broadcasts
- On each state transition, broadcast a `live/match` payload with matchId, teams, and state.
- On score updates/overrides, broadcast `live/score/red` or `live/score/blue`.
- For display changes, broadcast `live/display/control` with `DisplayControlAction`.

### 5) Frontend (rewrite)
- Integrate the new `/api/match-control` endpoints in the rewritten services.
- Update UI to reflect `MatchState` transitions and timer events.
- Subscribe to `BroadcastService` topics for live updates.

### 6) Tests and Validation
- Unit tests for state transitions and ranking update behavior.
- Manual flow: load -> activate -> start -> referee submit -> commit -> rankings updated.
- Verify WebSocket payload formats match frontend expectations.

## Edge Cases
- Prevent start if no active match.
- Reject commit if scores missing or still `NOT_SCORED`.
- Handle abort during timer with proper reset.
- Ensure override does not bypass commit guardrails.

## Deliverables
- Implemented `MatchControlApi` endpoints.
- Completed match control logic and broadcasts.
- New frontend services aligned to `/api/match-control`.
- Verified end-to-end flow with ranking update.
