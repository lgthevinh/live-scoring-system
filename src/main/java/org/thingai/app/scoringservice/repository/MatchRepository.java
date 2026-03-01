package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.match.Match;
import org.thingai.base.dao.Dao;

public class MatchRepository {
    private final Dao dao;

    public MatchRepository(Dao dao) {
        this.dao = dao;
    }

    public Match insertMatch(Match match) throws Exception {
        dao.insert(match);
        return match;
    }

    public Match updateMatch(Match match) throws Exception {
        dao.insertOrUpdate(match);
        return match;
    }

    public void deleteMatch(String matchId) throws Exception {
        dao.deleteByColumn(Match.class, "id", matchId);
    }

    public void deleteAllMatch() throws Exception {
        dao.deleteAll(Match.class);
    }

    public Match[] listMatches() throws Exception {
        return dao.readAll(Match.class);
    }

    public Match getMatchById(String matchId) throws Exception {
        Match[] matches = dao.query(Match.class, new String[]{"id"}, new String[]{matchId});
        if (matches != null && matches.length > 0) {
            return matches[0];
        }
        return null;
    }

    public Match getMatchByMatchCode(String matchCode) throws Exception {
        Match[] matches = dao.query(Match.class, new String[]{"matchCode"}, new String[]{matchCode});
        if (matches != null && matches.length > 0) {
            return matches[0];
        }
        return null;
    }
}
