package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.AllianceTeam;
import org.thingai.base.dao.Dao;

public class DaoAllianceTeam {
    private final Dao dao;

    public DaoAllianceTeam(Dao dao) {
        this.dao = dao;
    }

    public AllianceTeam insertAllianceTeam(AllianceTeam allianceTeam) throws Exception {
        dao.insertOrUpdate(allianceTeam);
        return allianceTeam;
    }

    public AllianceTeam updateAllianceTeam(AllianceTeam allianceTeam) throws Exception {
        dao.insertOrUpdate(allianceTeam);
        return allianceTeam;
    }

    public void deleteAllianceTeam(String allianceId, String teamId) throws Exception {
        AllianceTeam temp = new AllianceTeam();
        temp.setTeamId(teamId);
        temp.setAllianceId(allianceId);
        dao.delete(temp);
    }

    public void deleteAllianceTeamsByAllianceId(String allianceId) throws Exception {
        dao.deleteByColumn(AllianceTeam.class, "allianceId", allianceId);
    }

    public void deleteAllAllianceTeams() throws Exception {
        dao.deleteAll(AllianceTeam.class);
    }

    public AllianceTeam[] listAllianceTeams() throws Exception {
        return dao.readAll(AllianceTeam.class);
    }

    public AllianceTeam[] getAllianceTeamsByAllianceId(String allianceId) throws Exception {
        return dao.query(AllianceTeam.class, new String[]{"allianceId"}, new String[]{allianceId});
    }

    public AllianceTeam[] getAllianceTeamsByTeamId(String teamId) throws Exception {
        return dao.query(AllianceTeam.class, new String[]{"teamId"}, new String[]{teamId});
    }
}
