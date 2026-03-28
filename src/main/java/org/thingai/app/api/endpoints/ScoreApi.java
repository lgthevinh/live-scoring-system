package org.thingai.app.api.endpoints;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.ScoringService;

import java.util.Map;

@RestController
@RequestMapping("/api/scores")
public class ScoreApi {

    @GetMapping("/match/{matchId}")
    public ResponseEntity<Object> getMatchScore(@PathVariable String matchId) {
        return null;
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
        return null;
    }
}
