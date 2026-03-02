package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.ranking.RankingEntry;
import org.thingai.base.dao.Dao;

public class RankEntryRepository {
    private static Dao dao;

    public static void initialize(Dao daoInstance) {
        dao = daoInstance;
    }

    public static RankingEntry insertRankingEntry(RankingEntry entry) throws Exception {
        dao.insert(entry);
        return entry;
    }

    public static RankingEntry updateRankingEntry(RankingEntry entry) throws Exception {
        dao.insertOrUpdate(entry);
        return entry;
    }

    public static void deleteRankingEntry(String teamId) throws Exception {
        dao.deleteByColumn(RankingEntry.class, "teamId", teamId);
    }

    public static RankingEntry[] listRankingEntries() throws Exception {
        return dao.readAll(RankingEntry.class);
    }

    public static RankingEntry getRankingEntryById(String teamId) throws Exception {
        RankingEntry[] entries = dao.query(RankingEntry.class, new String[]{"teamId"}, new String[]{teamId});
        if (entries != null && entries.length > 0) {
            return entries[0];
        }
        return null;
    }

    public static void insertRankingEntries(RankingEntry[] entries) throws Exception {
        for (RankingEntry entry : entries) {
            dao.insert(entry);
        }
    }

    public static void deleteAllRankingEntries() throws Exception {
        dao.deleteAll(RankingEntry.class);
    }
}