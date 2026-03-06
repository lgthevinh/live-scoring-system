package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.ranking.RankingEntry;
import org.thingai.base.dao.Dao;

public class DaoRankEntry {
    private final Dao dao;

    public DaoRankEntry(Dao dao) {
        this.dao = dao;
    }

    public RankingEntry insertRankingEntry(RankingEntry entry) throws Exception {
        dao.insert(entry);
        return entry;
    }

    public RankingEntry updateRankingEntry(RankingEntry entry) throws Exception {
        dao.insertOrUpdate(entry);
        return entry;
    }

    public void deleteRankingEntry(String teamId) throws Exception {
        dao.deleteByColumn(RankingEntry.class, "teamId", teamId);
    }

    public RankingEntry[] listRankingEntries() throws Exception {
        return dao.readAll(RankingEntry.class);
    }

    public RankingEntry getRankingEntryById(String teamId) throws Exception {
        RankingEntry[] entries = dao.query(RankingEntry.class, new String[]{"teamId"}, new String[]{teamId});
        if (entries != null && entries.length > 0) {
            return entries[0];
        }
        return null;
    }

    public void insertRankingEntries(RankingEntry[] entries) throws Exception {
        for (RankingEntry entry : entries) {
            dao.insert(entry);
        }
    }

    public void deleteAllRankingEntries() throws Exception {
        dao.deleteAll(RankingEntry.class);
    }
}
