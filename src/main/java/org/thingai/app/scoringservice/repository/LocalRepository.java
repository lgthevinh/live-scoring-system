package org.thingai.app.scoringservice.repository;

import org.thingai.base.dao.Dao;

public class LocalRepository {
    private static DaoAuth daoAuth;
    private static DaoEvent daoEvent;
    private static DaoTeam daoTeam;
    private static DaoMatch daoMatch;
    private static DaoAllianceTeam daoAllianceTeam;
    private static DaoScore daoScore;
    private static DaoRankEntry daoRankEntry;

    /**
     * Initialize system-level DAOs backed by the main application database.
     * Must be called once at application startup before any event is loaded.
     */
    public static void initializeSystem(Dao dao) {
        daoAuth = new DaoAuth(dao);
        daoEvent = new DaoEvent(dao);
    }

    /**
     * Initialize event-level DAOs backed by the active event's database.
     * Called each time a new event is activated, replacing the previous instances.
     */
    public static void initializeEvent(Dao dao) {
        daoAuth = new DaoAuth(dao);
        daoTeam = new DaoTeam(dao);
        daoMatch = new DaoMatch(dao);
        daoAllianceTeam = new DaoAllianceTeam(dao);
        daoScore = new DaoScore(dao);
        daoRankEntry = new DaoRankEntry(dao);
    }

    public static DaoAuth authDao() {
        return daoAuth;
    }

    public static DaoEvent eventDao() {
        return daoEvent;
    }

    public static DaoTeam teamDao() {
        return daoTeam;
    }

    public static DaoMatch matchDao() {
        return daoMatch;
    }

    public static DaoAllianceTeam allianceTeamDao() {
        return daoAllianceTeam;
    }

    public static DaoScore scoreDao() {
        return daoScore;
    }

    public static DaoRankEntry rankEntryDao() {
        return daoRankEntry;
    }
}
