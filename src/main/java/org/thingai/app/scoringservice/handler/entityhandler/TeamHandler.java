package org.thingai.app.scoringservice.handler.entityhandler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.entity.team.Team;
import org.thingai.base.cache.LRUCache;
import org.thingai.base.dao.Dao;

public class TeamHandler {
    private Dao dao;
    
    public TeamHandler(Dao dao) {
        this.dao = dao;
    }

    public void addTeam(String teamId, String teamName, String teamSchool, String teamRegion, RequestCallback<Team> callback) {
        // Validate inputs
        if (teamId == null || teamId.trim().isEmpty()) {
            callback.onFailure(ErrorCode.CREATE_FAILED, "Team ID is required");
            return;
        }
        if (teamName == null || teamName.trim().isEmpty()) {
            callback.onFailure(ErrorCode.CREATE_FAILED, "Team name is required");
            return;
        }
        // teamSchool and teamRegion are optional but should not be null
        if (teamSchool == null) teamSchool = "";
        if (teamRegion == null) teamRegion = "";

        // Check for duplicate team ID
        Team[] existing = dao.query(Team.class, "id", teamId);
        if (existing != null && existing.length > 0) {
            callback.onFailure(ErrorCode.CREATE_FAILED, "Team ID already exists: " + teamId);
            return;
        }

        try {
            Team team = new Team();
            team.setTeamId(teamId);
            team.setTeamName(teamName);
            team.setTeamSchool(teamSchool);
            team.setTeamRegion(teamRegion);

            dao.insert(team);
            callback.onSuccess(team, "Team added successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.CREATE_FAILED, e.getMessage());
        }
    }

    public void addTeam(Team team, RequestCallback<Team> callback) {
        try {
            dao.insert(team);
            callback.onSuccess(team, "Team added successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.CREATE_FAILED, e.getMessage());
        }
    }

    public void addTeams(Team[] teams, RequestCallback<Boolean> callback) {
        if (teams == null || teams.length == 0) {
            callback.onFailure(ErrorCode.CREATE_FAILED, "No teams to add");
            return;
        }
        // Check for null elements
        for (int i = 0; i < teams.length; i++) {
            if (teams[i] == null) {
                callback.onFailure(ErrorCode.CREATE_FAILED, "Team at index " + i + " is null");
                return;
            }
            if (teams[i].getTeamId() == null || teams[i].getTeamId().trim().isEmpty()) {
                callback.onFailure(ErrorCode.CREATE_FAILED, "Team ID is required for team at index " + i);
                return;
            }
        }

        try {
            dao.insertBatch(teams);
            callback.onSuccess(true,"Teams added successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.CREATE_FAILED, e.getMessage());
        }
    }

    public void listTeams(RequestCallback<Team[]> callback) {
        try {
            Team[] teams = dao.readAll(Team.class);
            callback.onSuccess(teams, "Team list retrieved successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, e.getMessage());
        }
    }

    public void getTeamById(String teamId, RequestCallback<Team> callback) {
        try {
            Team[] results = dao.query(Team.class, "id", teamId);
            if (results == null || results.length == 0) {
                callback.onFailure(ErrorCode.NOT_FOUND, "Team not found with ID: " + teamId);
                return;
            }
            Team team = results[0];

            callback.onSuccess(team, "Team retrieved successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, e.getMessage());
        }
    }

    public void updateTeam(Team team, RequestCallback<Team> callback) {
        try {
            dao.insertOrUpdate(team);
            callback.onSuccess(team, "Team updated successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.UPDATE_FAILED, e.getMessage());
        }
    }

    public void deleteTeam(String teamId, RequestCallback<Void> callback) {
        try {
            dao.delete(Team.class, teamId);
            callback.onSuccess(null, "Team deleted successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DELETE_FAILED, e.getMessage());
        }
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }
}
