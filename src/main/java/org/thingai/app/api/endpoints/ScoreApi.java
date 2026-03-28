package org.thingai.app.api.endpoints;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.dto.ScoreDetailDto;
import org.thingai.app.scoringservice.entity.Score;

import java.util.Map;

@RestController
@RequestMapping("/api/scores")
public class ScoreApi {

    @GetMapping("/match/{matchId}")
    public ResponseEntity<Object> getMatchScore(@PathVariable String matchId) {
        if (matchId == null || matchId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "matchId is required."));
        }
        try {
            Score[] scores = ScoringService.scoreHandler().getScoresByMatchId(matchId);

            Score redScore = scores.length > 0 ? scores[0] : null;
            Score blueScore = scores.length > 1 ? scores[1] : null;
            Integer state = deriveScoreState(redScore, blueScore);

            return ResponseEntity.ok(Map.of(
                    "matchId", matchId,
                    "r", redScore,
                    "b", blueScore,
                    "state", state
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to load match score: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<Object> getAllScores() {
        try {
            return ResponseEntity.ok(ScoringService.scoreHandler().getAllScores());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to load scores: " + e.getMessage()));
        }
    }

    @GetMapping("/match/{matchId}/detail")
    public ResponseEntity<Object> getMatchScoreDetail(@PathVariable String matchId) {
        if (matchId == null || matchId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "matchId is required."));
        }
        try {
            ScoreDetailDto[] details = ScoringService.scoreHandler().getScoreDetailsByMatchId(matchId);
            return ResponseEntity.ok(Map.of(
                    "matchId", matchId,
                    "r", details.length > 0 ? details[0] : null,
                    "b", details.length > 1 ? details[1] : null
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to load score details: " + e.getMessage()));
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<Object> submitScore(@RequestBody String body) {
        if (body == null || body.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Score payload is required."));
        }
        if (ScoringService.liveScoreControl() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Scoring service not ready."));
        }

        ScoringService.liveScoreControl().handleScoreSubmit(body);
        return ResponseEntity.ok(Map.of("message", "Score submitted."));
    }

    @GetMapping("/define")
    public ResponseEntity<Object> getScoreUIDefinitions() {
        if (ScoringService.scoreHandler() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Scoring service not ready."));
        }
        return ResponseEntity.ok(ScoringService.scoreHandler().getScoreDefinitions());
    }

    private Integer deriveScoreState(Score redScore, Score blueScore) {
        Integer redState = redScore != null ? redScore.getState() : null;
        Integer blueState = blueScore != null ? blueScore.getState() : null;

        if (redState == null) {
            return blueState;
        }
        if (blueState == null) {
            return redState;
        }
        if (redState.equals(blueState)) {
            return redState;
        }
        return Math.max(redState, blueState);
    }
}
