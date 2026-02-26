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
            Team team = dao.query(Team.class, "id", teamId)[0];

            if (team == null) {
                callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Team not found with ID: " + teamId);
                return;
            }
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
