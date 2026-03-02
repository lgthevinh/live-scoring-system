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

## Websocket Events

Base topic: `live/`

### Match lifecycle events - Topic: `live/match`

- Payload:
    ```json
    {
      "state": 1, 2 or 3... in MatchLifeCycleState
      "matchId": "string",
      "red": [{teamId}, {teamId}],
      "blue": [{teamId}, {teamId}]
    }
    ```

### Score update events - Topic: `live/score`

- Payload:
  ```json
    {
      "matchId": "string",
      "red": {
        ...
      },
      "blue": {
        ...
      },
      "state": number
    }
  ```