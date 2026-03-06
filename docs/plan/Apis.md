# Scoring system APIs (Plan)

## API Endpoints

### Match and Schedule Management

- `POST /api/matches` - Create a new match, usually used for playoffs or special matches
- `DELETE /api/matches/{id}` - Delete a match
- `PUT /api/matches/{id}` - Update match details
- `GET /api/matches/{id}` - Get match details

- `POST /api/match/schedules/generate` - Generate match schedule
- `GET /api/match/schedules` - Get current match schedule
- `POST /api/match/schedules/clear` - Clear existing schedule

- `POST /api/match/playoffs/generate` - Generate playoff schedule
- `GET /api/match/playoffs` - Get current playoff schedule
- `POST /api/match/playoffs/clear` - Clear existing playoff schedule

### Scoring Endpoints

- `GET /api/scores/match/{matchId}` - Get current score for a match
    - Response:
      ```json
        {
          "matchId": "string",
          "red": {
            "score": number,
            "penalties": number,
            ...
          },
          "blue": {
            "score": number,
            "penalties": number,
            ...
          },
          "state": number
        },
      ```
- `GET /api/scores/current` - Get current scores of currently active matches
    - Response:
      ```json
          {
            "matchId": "string",
            "pre-match": {
              "red": 0, // 0 = No-show, 1 = No-robot, 2 = Shown
              "blue": 0
            },
            "red": {
              "score": number,
              "penalties": number,
              ...
            },
            "blue": {
              "score": number,
              "penalties": number,
              ...
            },
            "state": number
          },
      ```

### Ranking Endpoints

- `GET /api/rankings` - Get current team rankings
    - Response:
      ```json
        [
          {
            "teamId": "string",
            "teamName": "string",
            "matchesPlayed": number,
            "wins": number,
            "totalScore": number,
            "totalPenalties": number,
            "hightestScore": number,
            "rankingPoints": number,
            ...
          },
          ...
        ]
      ```
- `GET /api/rankings/{teamId}` - Get ranking details for a specific team
- `POST /api/rankings/calculate` - Trigger ranking calculation (if not done automatically after each match)
- `POST /api/rankings/clear` - Clear existing rankings (if not cleared automatically before recalculation)

### Referee Endpoints

- `POST /api/scores/prematch` - Submit pre-match alliance status
    - Request body:
        ```json
        {
          "matchId": "string",
          "allianceId": "string",
          "status": 0, 1, 2 // 0 = No-show, 1 = No-robot, 2 = Shown
        }
        ```
- `POST /api/scores/submit` - Submit score for a match
    - Request body:
        ```json
        {
          "matchId": "string",
          "allianceId": "string",
          "score": {
            // Score details (e.g., points, penalties)
          }
        }
        ```

### Match control endpoints

- `POST /api/control/load/{matchId}` - Load a match for scoring
- `POST /api/control/active` - Active a match for scoring
    - Optional params: `?matchId={matchId}` to specify which match to active, if not provided, will activate the
      currently loaded match
- `POST /api/control/start` - Start a match
- `POST /api/control/abort` - Abort a match
- `POST /api/control/commit` - Commit final score after both alliances submit
- `POST /api/control/allow-override` - Allow overriding score of current active match

### Team management

- `POST /api/teams` - Create a new team
- `POST /api/teams/import` - Import teams in csv format (teamId, teamName, teamSchool, teamRegion)
- `GET /api/teams` - Get list of teams
- `GET /api/teams/{id}` - Get team details
- `PUT /api/teams/{id}` - Update team details
- `DELETE /api/teams/{id}` - Delete a team

### Sync Endpoints

- `POST /api/sync/score` - Sync current score to external system
    - Request body:
        ```json
        {
          "state": "number",
          "matchId": "string",
          "r": {
            "score": number,
            "penalties": number,
            ...
          },
          "b": {
            "score": number,
            "penalties": number,
            ...
          }
        }
        ```
- `POST /api/sync/match` - Sync match status to external system
    - Request body:
        ```json
        {
          "state": "number",
          "matchId": "string",
          "fieldId": "string",
          "r": [{teamId}, {teamId}],
          "b": [{teamId}, {teamId}],
          "state": number // MatchLifeCycleState
        }
        ```

## Websocket Events

Base topic: `live/`

### Match lifecycle events - Topic: `live/match`

- Payload:
    ```json
    {
      "state": 1, 2 or 3... in MatchLifeCycleState
      "matchId": "string",
      "r": [{teamId}, {teamId}],
      "b": [{teamId}, {teamId}]
    }
    ```

### Score live update events (referee update to server) - Topic: `live/score/red` and `live/score/blue`

- Payload:
  ```json
    {
      "matchId": "string",
      "r": {
        ...
      },
      "b": {
        ...
      },
    }
  ```

### Display control events (for score display system) - Topic: `live/display`

- Score event - Topic `live/display/score`
    - Payload:
      ```json
        {
          "matchId": "string",
          "fieldId": "string",
          "r": {
            ...
          },
          "b": {
            ...
          },
          "state": number // MatchLifeCycleState
        }
      ```

- Ranking event - Topic `live/display/ranking`
    - Payload:
      ```json
        [
          {
            "teamId": "string",
            "teamName": "string",
            "matchesPlayed": number,
            "wins": number,
            "totalScore": number,
            "totalPenalties": number,
            "hightestScore": number,
            "rankingPoints": number,
            ...
          },
          ...
        ]
      ```

- Control event - Topic `live/display/control`
    - Payload:
      ```json
        {
          "action": 1, 2, 3... in DisplayControlAction,
          "data": {
            // Optional data for the control action, e.g., matchId for loading a match
          }
        }
      ```