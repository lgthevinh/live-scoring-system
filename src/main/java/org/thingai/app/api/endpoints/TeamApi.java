package org.thingai.app.api.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.entity.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.api.utils.ResponseEntityUtil.createErrorResponse;
import static org.thingai.app.api.utils.ResponseEntityUtil.getObjectResponse;

@RestController
@RequestMapping("/api/teams")
public class TeamApi {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("")
    public ResponseEntity<Object> createTeam(@RequestBody Team team) {
        if (team == null || hasMissingFields(team)) {
            return ResponseEntity.badRequest().body(Map.of("error", "teamId, teamName, teamSchool, teamRegion are required."));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.teamHandler().createTeam(
                team.getTeamId(),
                team.getTeamName(),
                team.getTeamSchool(),
                team.getTeamRegion(),
                new RequestCallback<Team>() {
                    @Override
                    public void onSuccess(Team responseObject, String message) {
                        future.complete(ResponseEntity.ok(responseObject));
                    }

                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        future.complete(createErrorResponse(errorCode, errorMessage));
                    }
                });

        return getObjectResponse(future);
    }

    @GetMapping("")
    public ResponseEntity<Object> listTeams() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.teamHandler().listTeams(new RequestCallback<Team[]>() {
            @Override
            public void onSuccess(Team[] responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getTeam(@PathVariable String id) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.teamHandler().getTeam(id, new RequestCallback<Team>() {
            @Override
            public void onSuccess(Team responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> updateTeam(@PathVariable String id, @RequestBody Team team) {
        return updateTeamInternal(team, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteTeam(@PathVariable String id) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.teamHandler().deleteTeam(id, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void responseObject, String message) {
                future.complete(ResponseEntity.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    @PostMapping("/import")
    public ResponseEntity<Object> importTeams(@RequestBody String body) {
        Team[] teams;
        try {
            teams = parseTeamsFromBody(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.teamHandler().importTeams(teams, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void responseObject, String message) {
                future.complete(ResponseEntity.ok(Map.of("message", message, "count", teams.length)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    private ResponseEntity<Object> updateTeamInternal(Team team, String teamId) {
        if (team == null || isBlank(teamId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "teamId is required."));
        }

        if (team.getTeamId() != null && !teamId.equals(team.getTeamId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "teamId in path and body must match."));
        }

        team.setTeamId(teamId);

        if (hasMissingFields(team)) {
            return ResponseEntity.badRequest().body(Map.of("error", "teamId, teamName, teamSchool, teamRegion are required."));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.teamHandler().updateTeam(team, new RequestCallback<Team>() {
            @Override
            public void onSuccess(Team responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    private Team[] parseTeamsFromBody(String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Request body is empty.");
        }

        if (trimmed.startsWith("[")) {
            try {
                Team[] teams = objectMapper.readValue(trimmed, Team[].class);
                if (teams == null || teams.length == 0) {
                    throw new IllegalArgumentException("No teams provided.");
                }
                return teams;
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid JSON array payload.");
            }
        }

        if (trimmed.startsWith("{")) {
            try {
                Team team = objectMapper.readValue(trimmed, Team.class);
                if (team == null) {
                    throw new IllegalArgumentException("Invalid team payload.");
                }
                return new Team[]{team};
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid JSON team payload.");
            }
        }

        return parseTeamsFromCsv(trimmed);
    }

    private Team[] parseTeamsFromCsv(String csvBody) {
        String[] lines = csvBody.split("\\r?\\n");
        List<Team> teams = new ArrayList<>();

        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            List<String> fields = parseCsvLine(trimmed);
            if (fields.size() < 4) {
                throw new IllegalArgumentException("CSV row must have 4 columns: teamId, teamName, teamSchool, teamRegion.");
            }

            if (isHeaderRow(fields)) {
                continue;
            }

            String teamId = fields.get(0).trim();
            String teamName = fields.get(1).trim();
            String teamSchool = fields.get(2).trim();
            String teamRegion = fields.get(3).trim();

            if (isBlank(teamId) || isBlank(teamName) || isBlank(teamSchool) || isBlank(teamRegion)) {
                throw new IllegalArgumentException("CSV row contains empty team fields.");
            }

            teams.add(new Team(teamId, teamName, teamSchool, teamRegion));
        }

        if (teams.isEmpty()) {
            throw new IllegalArgumentException("No valid team rows found in CSV.");
        }

        return teams.toArray(new Team[0]);
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
                continue;
            }

            if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(c);
        }

        fields.add(current.toString());
        return fields;
    }

    private boolean isHeaderRow(List<String> fields) {
        if (fields.size() < 2) {
            return false;
        }

        String first = fields.get(0).trim().toLowerCase();
        String second = fields.get(1).trim().toLowerCase();
        return (first.equals("teamid") || first.equals("id"))
                && (second.equals("teamname") || second.equals("name"));
    }

    private boolean hasMissingFields(Team team) {
        return isBlank(team.getTeamId())
                || isBlank(team.getTeamName())
                || isBlank(team.getTeamSchool())
                || isBlank(team.getTeamRegion());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
