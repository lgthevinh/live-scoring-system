package org.thingai.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.entity.team.Team;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.controller.utils.ResponseEntityUtil.getObjectResponse;

@RestController
@RequestMapping("/api/team")
public class TeamController {

    @PostMapping("/create")
    public ResponseEntity<Object> createTeam(@RequestBody Map<String, Object> requestBody) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        String teamId = requestBody.get("teamId").toString();
        String teamName = requestBody.get("teamName").toString();
        String teamSchool = requestBody.get("teamSchool").toString();
        String teamRegion = requestBody.get("teamRegion").toString();

        ScoringService.teamHandler().handleCreateTeam(teamId, teamName, teamSchool, teamRegion, new RequestCallback<Team>() {
            @Override
            public void onSuccess(Team team, String message) {
                future.complete(ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", message, "team", team)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }

    @GetMapping("/list")
    public ResponseEntity<Object> listTeams() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.teamHandler().handleListTeams(new RequestCallback<Team[]>() {
            @Override
            public void onSuccess(Team[] teams, String message) {
                future.complete(ResponseEntity.ok(teams));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getTeam(@PathVariable String id) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.teamHandler().handleGetTeam(id, new RequestCallback<Team>() {
            @Override
            public void onSuccess(Team team, String message) {
                future.complete(ResponseEntity.ok(team));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                HttpStatus status = errorCode == 105 ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;
                future.complete(ResponseEntity.status(status).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }

    @PutMapping("/update")
    public ResponseEntity<Object> updateTeam(@RequestBody Team team) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.teamHandler().handleUpdateTeam(team, new RequestCallback<Team>() {
            @Override
            public void onSuccess(Team updatedTeam, String message) {
                future.complete(ResponseEntity.ok(Map.of("message", message, "team", updatedTeam)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Object> deleteTeam(@PathVariable String id) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.teamHandler().handleDeleteTeam(id, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void ignored, String message) {
                future.complete(ResponseEntity.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }

    @PostMapping("/import")
    public ResponseEntity<Object> importTeams(@RequestBody Team[] teams) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.teamHandler().handleImportTeams(teams, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void ignored, String message) {
                future.complete(ResponseEntity.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }
}
