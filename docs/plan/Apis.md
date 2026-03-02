# Scoring system APIs (Plan)

## API Endpoints

### Match and Schedule Management

- `POST /api/matches` - Create a new match
- `DELETE /api/matches/{id}` - Delete a match
- `PUT /api/matches/{id}` - Update match details
- `GET /api/matches/{id}` - Get match details

- `POST /api/match/schedules/generate` - Generate match schedule
- `GET /api/match/schedules` - Get current match schedule
- `POST /api/match/schedules/clear` - Clear existing schedule

- `POST /api/match/playoffs/generate` - Generate playoff schedule
- `GET /api/match/playoffs` - Get current playoff schedule
- `POST /api/match/playoffs/clear` - Clear existing playoff schedule

### Scoring and Referee Actions

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
- `POST /api/scores/commit` - Commit final score after both alliances submit
- `GET /api/scores` - Get current score for an alliance/match if available
    - Parameters: `matchId=string` or `allianceId=string`
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
        }
        ```
- `GET /api/scores/current` - Get current scores of currently active matches
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