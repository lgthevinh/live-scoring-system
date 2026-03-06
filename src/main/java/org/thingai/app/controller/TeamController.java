package org.thingai.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.entity.team.Team;
import org.thingai.app.scoringservice.repository.LocalRepository;
import org.thingai.base.dao.exceptions.DaoException;
import org.thingai.base.dao.exceptions.DaoQueryException;

import java.util.Map;

import static org.thingai.app.controller.utils.ResponseEntityUtil.createErrorResponse;

@RestController
@RequestMapping("/api/team")
public class TeamController {

    @PostMapping("/create")
    public ResponseEntity<Object> createTeam(@RequestBody Map<String, Object> requestBody) {
        try {
            String teamId = requestBody.get("teamId").toString();
            String teamName = requestBody.get("teamName").toString();
            String teamSchool = requestBody.get("teamSchool").toString();
            String teamRegion = requestBody.get("teamRegion").toString();

            Team newTeam = LocalRepository.teamDao().insertTeam(teamId, teamName, teamSchool, teamRegion);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Team created successfully",
                            "team", newTeam
                    ));
        } catch (DaoQueryException e) {
            return createErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid team data:" + e.getMessage());
        } catch (DaoException e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Database error: " + e.getMessage());
        } catch (Exception e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Object> listTeams() {
        try {
            Team[] teams = LocalRepository.teamDao().listTeams();
            return ResponseEntity.ok(teams);
        } catch (DaoQueryException e) {
            return createErrorResponse(HttpStatus.BAD_REQUEST.value(), "Failed to retrieve teams: " + e.getMessage());
        } catch (DaoException e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Database error: " + e.getMessage());
        } catch (Exception e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getTeam(@PathVariable String id) {
        try {
            Team team = LocalRepository.teamDao().getTeamById(id);
            if (team != null) {
                return ResponseEntity.ok(team);
            } else {
                return createErrorResponse(HttpStatus.NOT_FOUND.value(), "Team not found with id: " + id);
            }
        } catch (DaoQueryException e) {
            return createErrorResponse(HttpStatus.BAD_REQUEST.value(), "Failed to retrieve team: " + e.getMessage());
        } catch (DaoException e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Database error: " + e.getMessage());
        } catch (Exception e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unexpected error: " + e.getMessage());
        }
    }

    @PutMapping("/update")
    public ResponseEntity<Object> updateTeam(@RequestBody Team team) {
        try {
            Team updatedTeam = LocalRepository.teamDao().updateTeam(team);
            return ResponseEntity.ok(Map.of(
                    "message", "Team updated successfully",
                    "team", updatedTeam
            ));
        } catch (DaoQueryException e) {
            return createErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid team data: " + e.getMessage());
        } catch (DaoException e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Database error: " + e.getMessage());
        } catch (Exception e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unexpected error: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Object> deleteTeam(@PathVariable String id) {
        try {
            LocalRepository.teamDao().deleteTeam(id);
            return ResponseEntity.ok(Map.of("message", "Team deleted successfully"));
        } catch (DaoQueryException e) {
            return createErrorResponse(HttpStatus.BAD_REQUEST.value(), "Failed to delete team: " + e.getMessage());
        } catch (DaoException e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Database error: " + e.getMessage());
        } catch (Exception e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unexpected error: " + e.getMessage());
        }
    }

    @PostMapping("/import")
    public ResponseEntity<Object> importTeams() {
        return null;
    }
}