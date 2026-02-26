package org.thingai.app.scoringservice.handler.entityhandler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.entity.team.Team;
import org.thingai.base.cache.LRUCache;
import org.thingai.base.dao.Dao;

public class TeamHandler {
    private final LRUCache<String, Team> teamCache;

    private Dao dao;
    
    public TeamHandler(Dao dao, LRUCache<String, Team> teamCache) {
        this.dao = dao;
        this.teamCache = teamCache;
    }

    public void addTeam(String teamId, String teamName, String teamSchool, String teamRegion, RequestCallback<Team> callback) {
        try {
            Team team = new Team();
            team.setTeamId(teamId);
            team.setTeamName(teamName);
            team.setTeamSchool(teamSchool);
            team.setTeamRegion(teamRegion);

            dao.insertOrUpdate(team);
            // Add the new team to the cache
            teamCache.put(teamId, team);
            callback.onSuccess(team, "Team added successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.CREATE_FAILED, e.getMessage());
        }
    }

    public void addTeam(Team team, RequestCallback<Team> callback) {
        try {
            dao.insertOrUpdate(team);
            // Add the new team to the cache
            teamCache.put(team.getTeamId(), team);
            callback.onSuccess(team, "Team added successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.CREATE_FAILED, e.getMessage());
        }
    }

    public void addTeams(Team[] teams, RequestCallback<Boolean> callback) {
        try {
            for (Team team : teams) {
                dao.insertOrUpdate(team);
                // Add each new team to the cache
                teamCache.put(team.getTeamId(), team);
            }
            callback.onSuccess(true,"Teams added successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.CREATE_FAILED, e.getMessage());
        }
    }

    public void listTeams(RequestCallback<Team[]> callback) {
        try {
            Team[] teams = dao.readAll(Team.class);
            // "Warm up" the cache by adding all teams to it
            for (Team team : teams) {
                teamCache.put(team.getTeamId(), team);
            }
            callback.onSuccess(teams, "Team list retrieved successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, e.getMessage());
        }
    }

    public void getTeamById(String teamId, RequestCallback<Team> callback) {
        // 1. Check the cache first (Read-Through strategy)
        Team cachedTeam = teamCache.get(teamId);
        if (cachedTeam != null) {
            callback.onSuccess(cachedTeam, "Team retrieved successfully from cache");
            return;
        }

        // 2. If not in cache, fetch from the database
        try {
            Team team = dao.query(Team.class, "id", teamId)[0];
            if (team != null) {
                // 3. Add the fetched team to the cache for next time
                teamCache.put(teamId, team);
                callback.onSuccess(team, "Team retrieved successfully");
            } else {
                callback.onFailure(ErrorCode.NOT_FOUND, "Team not found");
            }
        } catch (Exception e) {
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, e.getMessage());
        }
    }

    public void updateTeam(Team team, RequestCallback<Team> callback) {
        try {
            dao.insertOrUpdate(team);
            // Update the cache with the new team data (Write-Through strategy)
            teamCache.put(team.getTeamId(), team);
            callback.onSuccess(team, "Team updated successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.UPDATE_FAILED, e.getMessage());
        }
    }

    public void deleteTeam(String teamId, RequestCallback<Void> callback) {
        try {
            dao.delete(Team.class, teamId);
            // Invalidate the cache by removing the deleted team
            teamCache.remove(teamId);
            callback.onSuccess(null, "Team deleted successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DELETE_FAILED, e.getMessage());
        }
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }

    public void clearCache() {
        teamCache.clear();
    }
}
