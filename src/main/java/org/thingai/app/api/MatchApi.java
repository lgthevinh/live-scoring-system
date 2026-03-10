package org.thingai.app.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.entity.Match;

import java.util.Map;

@RestController
@RequestMapping("/api/match")
public class MatchApi {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/create")
    public ResponseEntity<Object> createMatch(@RequestBody Map<String, Object> request) {
        return null;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getMatch(@PathVariable String id) {
        return null;
    }

    @GetMapping("")
    public ResponseEntity<Object> listMatches() {
        return null;
    }

    @GetMapping("/list/{matchType}")
    public ResponseEntity<Object> listMatchesByType(@PathVariable int matchType) {
        return null;
    }

    @GetMapping("/list/details/{matchType}")
    public ResponseEntity<Object> listMatchDetails(@PathVariable int matchType, @RequestParam(required = false) boolean withScore) {
        return null;
    }

    @PutMapping("/update")
    public ResponseEntity<Object> updateMatch(@RequestBody Match match) {
        return null;
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Object> deleteMatch(@PathVariable String id) {
        return null;
    }

    @PostMapping("/schedule/generate")
    public ResponseEntity<Object> generateSchedule(@RequestBody Map<String, Object> request) {
        return null;
    }

    @PostMapping("/playoff/generate")
    public ResponseEntity<Object> generatePlayoffSchedule(@RequestBody Map<String, Object> request) {
        return null;
    }
}

