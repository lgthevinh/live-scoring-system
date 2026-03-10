package org.thingai.app.scoringservice.handler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.entity.Team;
import org.thingai.app.scoringservice.repository.LocalRepository;

public class TeamHandler {

    public void createTeam(String teamId, String teamName, String teamSchool, String teamRegion, RequestCallback<Team> callback) {
        try {
            Team team = LocalRepository.teamDao().insertTeam(teamId, teamName, teamSchool, teamRegion);
            callback.onSuccess(team, "Team created successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_CREATE_FAILED, "Failed to create team: " + e.getMessage());
        }
    }

    public void listTeams(RequestCallback<Team[]> callback) {
        try {
            Team[] teams = LocalRepository.teamDao().listTeams();
            callback.onSuccess(teams, "Teams retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Failed to retrieve teams: " + e.getMessage());
        }
    }

    public void getTeam(String teamId, RequestCallback<Team> callback) {
        try {
            Team team = LocalRepository.teamDao().getTeamById(teamId);
            if (team == null) {
                callback.onFailure(ErrorCode.DAO_NOT_FOUND, "Team not found with id: " + teamId);
                return;
            }
            callback.onSuccess(team, "Team retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Failed to retrieve team: " + e.getMessage());
        }
    }

    public void updateTeam(Team team, RequestCallback<Team> callback) {
        try {
            Team updatedTeam = LocalRepository.teamDao().updateTeam(team);
            callback.onSuccess(updatedTeam, "Team updated successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_UPDATE_FAILED, "Failed to update team: " + e.getMessage());
        }
    }

    public void deleteTeam(String teamId, RequestCallback<Void> callback) {
        try {
            LocalRepository.teamDao().deleteTeam(teamId);
            callback.onSuccess(null, "Team deleted successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_DELETE_FAILED, "Failed to delete team: " + e.getMessage());
        }
    }

    public void importTeams(Team[] teams, RequestCallback<Void> callback) {
        try {
            LocalRepository.teamDao().insertTeams(teams);
            callback.onSuccess(null, "Teams imported successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_CREATE_FAILED, "Failed to import teams: " + e.getMessage());
        }
    }
}
