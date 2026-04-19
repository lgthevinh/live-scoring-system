package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.AccountRole;
import org.thingai.app.scoringservice.entity.AuthData;
import org.thingai.app.scoringservice.entity.DbMapEntity;
import org.thingai.app.scoringservice.entity.Event;
import org.thingai.app.scoringservice.entity.AllianceTeam;
import org.thingai.app.scoringservice.entity.Match;
import org.thingai.app.scoringservice.entity.RankingEntry;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.entity.Team;
import org.thingai.base.dao.Dao;
import org.thingai.platform.dao.DaoFile;
import org.thingai.platform.dao.DaoSqlite;

public class LocalRepository {
    private static Dao systemDatabase;
    private static Dao eventDatabase;
    private static DaoFile eventFileStore;

    private static DaoAuth daoAuth;
    private static DaoEvent daoEvent;
    private static DaoTeam daoTeam;
    private static DaoMatch daoMatch;
    private static DaoAllianceTeam daoAllianceTeam;
    private static DaoScore daoScore;
    private static DaoRankEntry daoRankEntry;

    /**
     * Opens the system database, registers system-level entities, and
     * initializes system-level DAOs. Must be called once at startup.
     */
    public static void initializeSystem(String dbPath) {
        systemDatabase = new DaoSqlite(dbPath);
        systemDatabase.initDao(new Class[]{
                Event.class,
                AuthData.class,
                AccountRole.class,
                DbMapEntity.class
        });
        daoAuth = new DaoAuth(systemDatabase);
        daoEvent = new DaoEvent(systemDatabase);
    }

    /**
     * Opens the event database for the given event code, registers event-level
     * entities, and initializes event-level DAOs. Called each time an event is activated.
     */
    public static void initializeEvent(String eventCode) {
        eventDatabase = new DaoSqlite(eventCode + ".db");
        eventDatabase.initDao(new Class[]{
                Match.class,
                AllianceTeam.class,
                Team.class,
                Score.class,
                RankingEntry.class,
                DbMapEntity.class
        });
        eventFileStore = new DaoFile("files/" + eventCode);
        daoAuth = new DaoAuth(eventDatabase);
        daoTeam = new DaoTeam(eventDatabase);
        daoMatch = new DaoMatch(eventDatabase);
        daoAllianceTeam = new DaoAllianceTeam(eventDatabase);
        daoScore = new DaoScore(eventDatabase);
        daoRankEntry = new DaoRankEntry(eventDatabase);
    }

    // --- Raw database connections ---

    public static Dao systemDatabase() {
        return systemDatabase;
    }

    public static Dao eventDatabase() {
        return eventDatabase;
    }

    public static DaoFile eventFileStore() {
        return eventFileStore;
    }

    // --- Entity-level DAO accessors ---

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
