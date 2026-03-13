package org.thingai.app.api.v1;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.entity.Team;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.api.utils.ResponseEntityUtil.createErrorResponse;
import static org.thingai.app.api.utils.ResponseEntityUtil.getObjectResponse;

@RestController
@RequestMapping("/api/v1/team")
public class TeamApi {

    @PostMapping("/create")
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

    @GetMapping("/list")
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

    @PutMapping("/update")
    public ResponseEntity<Object> updateTeam(@RequestBody Team team) {
        String teamId = team != null ? team.getTeamId() : null;
        return updateTeamInternal(team, teamId);
    }

    @DeleteMapping("/delete/{id}")
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

    private ResponseEntity<Object> updateTeamInternal(Team team, String teamId) {
        if (team == null || isBlank(teamId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "teamId is required."));
        }

        if (team.getTeamId() != null && !teamId.equals(team.getTeamId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "teamId in body must match."));
        }

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
