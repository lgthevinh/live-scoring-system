package org.thingai.app.api.v2;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scores")
public class ScoreApi {

    @GetMapping("/match/{matchId}")
    public ResponseEntity<Object> getMatchScore(@PathVariable String matchId) {
        return null;
    }

    @PostMapping("/submit")
    public ResponseEntity<Object> submitScore(@RequestBody String body) {
        return null;
    }

    @GetMapping("/define")
    public ResponseEntity<Object> getScoreUIDefinitions() {
        return null;
    }
}
