package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.team.Team;
import org.thingai.base.dao.Dao;

public class TeamRepository {
    private static Dao dao;

    public static void initialize(Dao daoInstance) {
        dao = daoInstance;
    }

    public static Team insertTeam(String teamId, String teamName, String teamSchool, String teamRegion) throws Exception {
        Team team = new Team(teamId, teamName, teamSchool, teamRegion);
        dao.insert(team);
        return team;
    }

    public static Team insertTeam(Team team) throws Exception {
        dao.insert(team);
        return team;
    }

    public static void insertTeams(Team[] teams) throws Exception {
        for (Team team : teams) {
            dao.insert(team);
        }
    }

    public static Team updateTeam(Team team) throws Exception {
        dao.insertOrUpdate(team);
        return team;
    }

    public static void deleteTeam(String teamId) throws Exception {
        dao.deleteByColumn(Team.class, "id", teamId);
    }

    public static Team[] listTeams() throws Exception {
        return dao.readAll(Team.class);
    }

    public static Team getTeamById(String teamId) throws Exception {
        Team[] teams = dao.query(Team.class, new String[]{"id"}, new String[]{teamId});
        if (teams != null && teams.length > 0) {
            return teams[0];
        }
        return null;
    }
}