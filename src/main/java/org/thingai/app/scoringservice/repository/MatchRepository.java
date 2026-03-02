package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.match.AllianceTeam;
import org.thingai.app.scoringservice.entity.match.Match;
import org.thingai.base.dao.Dao;

/*
 * Repository class for Match entity, providing CRUD operations and additional query methods.
 * This class interacts with the Dao to perform database operations related to Match entities.
 * It also provides a method to get detailed information about a match, including the teams in
 * the red and blue alliances.
 */
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

    public static MatchDetailDto getMatchDetailById(String matchId) throws Exception {
        Match match = getMatchById(matchId);
        if (match == null) {
            return null;
        }

        MatchDetailDto matchDetailDto = new MatchDetailDto();
        matchDetailDto.setMatch(match);

        AllianceTeam[] redAlliance = AllianceTeamRepository.getAllianceTeamsByAllianceId(matchId + "_R");
        AllianceTeam[] blueAlliance = AllianceTeamRepository.getAllianceTeamsByAllianceId(matchId + "_B");

        matchDetailDto.setRedAllianceTeams(redAlliance);
        matchDetailDto.setBlueAllianceTeams(blueAlliance);

        return matchDetailDto;
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