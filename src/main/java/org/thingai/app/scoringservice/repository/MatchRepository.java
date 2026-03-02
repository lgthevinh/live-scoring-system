package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.match.Match;
import org.thingai.base.dao.Dao;

public class MatchRepository {
    private static Dao dao;

    public static void initialize(Dao daoInstance) {
        dao = daoInstance;
    }

    public static Match insertMatch(Match match) throws Exception {
        dao.insert(match);
        return match;
    }

    public static Match updateMatch(Match match) throws Exception {
        dao.insertOrUpdate(match);
        return match;
    }

    public static void deleteMatch(String matchId) throws Exception {
        dao.deleteByColumn(Match.class, "id", matchId);
    }

    public static void deleteAllMatch() throws Exception {
        dao.deleteAll(Match.class);
    }

    public static Match[] listMatches() throws Exception {
        return dao.readAll(Match.class);
    }

    public static Match getMatchById(String matchId) throws Exception {
        Match[] matches = dao.query(Match.class, new String[]{"id"}, new String[]{matchId});
        if (matches != null && matches.length > 0) {
            return matches[0];
        }
        return null;
    }

    public static Match getMatchByMatchCode(String matchCode) throws Exception {
        Match[] matches = dao.query(Match.class, new String[]{"matchCode"}, new String[]{matchCode});
        if (matches != null && matches.length > 0) {
            return matches[0];
        }
        return null;
    }

    public static Match[] getMatchesByType(int matchType) throws Exception {
        if (matchType == 2) { // PLAYOFF
            return dao.query(Match.class, "SELECT * FROM match WHERE NOT matchType = 1");
        } else {
            return dao.query(Match.class, new String[]{"matchType"}, new String[]{String.valueOf(matchType)});
        }
    }
}