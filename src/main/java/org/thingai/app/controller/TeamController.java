package org.thingai.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.entity.team.Team;
import org.thingai.app.scoringservice.repository.TeamRepository;
import org.thingai.base.dao.exceptions.DaoException;
import org.thingai.base.dao.exceptions.DaoQueryException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.controller.utils.ResponseEntityUtil.createErrorResponse;
import static org.thingai.app.controller.utils.ResponseEntityUtil.getObjectResponse;

@RestController
@RequestMapping("/api/team")
public class TeamController {

    @PostMapping("/create")
    public ResponseEntity<Object> createTeam(@RequestBody Map<String, Object> requestBody) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        try {
            String teamId = requestBody.get("teamId").toString();
            String teamName = requestBody.get("teamName").toString();
            String teamSchool = requestBody.get("teamSchool").toString();
            String teamRegion = requestBody.get("teamRegion").toString();

            Team newTeam = TeamRepository.insertTeam(teamId, teamName, teamSchool, teamRegion);
            future.complete(ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Team created successfully",
                            "team", newTeam
                    )));
        } catch (DaoQueryException e) {
            future.complete(createErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid team data:" + e.getMessage()));
        } catch (DaoException e) {
            future.complete(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Database error: " + e.getMessage()));
        } catch (Exception e) {
            future.complete(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unexpected error: " + e.getMessage()));
        }
        return getObjectResponse(future);
    }

    @GetMapping("/list")
    public ResponseEntity<Object> listTeams() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        try {
            Team[] teams = TeamRepository.listTeams();
            future.complete(ResponseEntity.ok(teams));
        } catch (DaoQueryException e) {
            future.complete(createErrorResponse(HttpStatus.BAD_REQUEST.value(), "Failed to retrieve teams: " + e.getMessage()));
        } catch (DaoException e) {
            future.complete(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Database error: " + e.getMessage()));
        } catch (Exception e) {
            future.complete(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unexpected error: " + e.getMessage()));
        }

        return getObjectResponse(future);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getTeam(@PathVariable String id) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        try {
            Team team = TeamRepository.getTeamById(id);
            if (team != null) {
                future.complete(ResponseEntity.ok(team));
            } else {
                future.complete(createErrorResponse(HttpStatus.NOT_FOUND.value(), "Team not found with id: " + id));
            }
        } catch (DaoQueryException e) {
            future.complete(createErrorResponse(HttpStatus.BAD_REQUEST.value(), "Failed to retrieve team: " + e.getMessage()));
        } catch (DaoException e) {
            future.complete(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Database error: " + e.getMessage()));
        } catch (Exception e) {
            future.complete(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unexpected error: " + e.getMessage()));
        }

        return getObjectResponse(future);
    }

    @PutMapping("/update")
    public ResponseEntity<Object> updateTeam(@RequestBody Team team) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        try {
            Team updatedTeam = TeamRepository.updateTeam(team);
            future.complete(ResponseEntity.ok(Map.of(
                    "message", "Team updated successfully",
                    "team", updatedTeam
            )));
        } catch (DaoQueryException e) {
            future.complete(createErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid team data: " + e.getMessage()));
        } catch (DaoException e) {
            future.complete(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Database error: " + e.getMessage()));
        } catch (Exception e) {
            future.complete(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unexpected error: " + e.getMessage()));
        }

        return getObjectResponse(future);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Object> deleteTeam(@PathVariable String id) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        try {
            TeamRepository.deleteTeam(id);
            future.complete(ResponseEntity.ok(Map.of("message", "Team deleted successfully")));
        } catch (DaoQueryException e) {
            future.complete(createErrorResponse(HttpStatus.BAD_REQUEST.value(), "Failed to delete team: " + e.getMessage()));
        } catch (DaoException e) {
            future.complete(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Database error: " + e.getMessage()));
        } catch (Exception e) {
            future.complete(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unexpected error: " + e.getMessage()));
        }

        return getObjectResponse(future);
    }

    @PostMapping("/import")
    public ResponseEntity<Object> importTeams() {
        return null;
    }
}