package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.match.AllianceTeam;
import org.thingai.base.dao.Dao;

public class AllianceTeamRepository {
    private static Dao dao;

    public static void initialize(Dao daoInstance) {
        dao = daoInstance;
    }

    public static AllianceTeam insertAllianceTeam(AllianceTeam allianceTeam) throws Exception {
        dao.insert(allianceTeam);
        return allianceTeam;
    }

    public static AllianceTeam updateAllianceTeam(AllianceTeam allianceTeam) throws Exception {
        dao.insertOrUpdate(allianceTeam);
        return allianceTeam;
    }

    public static void deleteAllianceTeam(String allianceId, String teamId) throws Exception {
        AllianceTeam temp = new AllianceTeam();
        temp.setTeamId(teamId);
        temp.setAllianceId(allianceId);
        dao.delete(temp);
    }

    public static void deleteAllianceTeamsByAllianceId(String allianceId) throws Exception {
        dao.deleteByColumn(AllianceTeam.class, "allianceId", allianceId);
    }

    public static void deleteAllAllianceTeams() throws Exception {
        dao.deleteAll(AllianceTeam.class);
    }

    public static AllianceTeam[] listAllianceTeams() throws Exception {
        return dao.readAll(AllianceTeam.class);
    }

    public static AllianceTeam[] getAllianceTeamsByAllianceId(String allianceId) throws Exception {
        return dao.query(AllianceTeam.class, new String[]{"allianceId"}, new String[]{allianceId});
    }

    public static AllianceTeam[] getAllianceTeamsByTeamId(String teamId) throws Exception {
        return dao.query(AllianceTeam.class, new String[]{"teamId"}, new String[]{teamId});
    }
}