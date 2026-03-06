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
public class DaoMatch {
    private final Dao dao;

    public DaoMatch(Dao dao) {
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

    public MatchDetailDto getMatchDetailById(String matchId) throws Exception {
        Match match = getMatchById(matchId);
        if (match == null) {
            return null;
        }

        MatchDetailDto matchDetailDto = new MatchDetailDto();
        matchDetailDto.setMatch(match);

        AllianceTeam[] redAlliance = LocalRepository.allianceTeamDao().getAllianceTeamsByAllianceId(matchId + "_R");
        AllianceTeam[] blueAlliance = LocalRepository.allianceTeamDao().getAllianceTeamsByAllianceId(matchId + "_B");

        matchDetailDto.setRedAllianceTeams(redAlliance);
        matchDetailDto.setBlueAllianceTeams(blueAlliance);

        return matchDetailDto;
    }

    public Match getMatchByMatchCode(String matchCode) throws Exception {
        Match[] matches = dao.query(Match.class, new String[]{"matchCode"}, new String[]{matchCode});
        if (matches != null && matches.length > 0) {
            return matches[0];
        }
        return null;
    }

    public Match[] getMatchesByType(int matchType) throws Exception {
        if (matchType == 2) { // PLAYOFF
            return dao.query(Match.class, "SELECT * FROM match WHERE NOT matchType = 1");
        } else {
            return dao.query(Match.class, new String[]{"matchType"}, new String[]{String.valueOf(matchType)});
        }
    }
}
