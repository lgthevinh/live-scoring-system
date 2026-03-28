package org.thingai.app.scoringservice.handler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.define.MatchType;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.AllianceTeam;
import org.thingai.app.scoringservice.entity.Match;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.entity.Team;
import org.thingai.app.scoringservice.repository.LocalRepository;

import java.util.*;

public class MatchHandler {

    public void createMatch(int matchType,
                            int matchNumber,
                            int fieldNumber,
                            String matchStartTime,
                            String[] redTeamIds,
                            String[] blueTeamIds,
                            RequestCallback<Match> callback) {
        try {
            if (!isEventReady()) {
                callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "No active event database.");
                return;
            }

            if (redTeamIds == null || redTeamIds.length == 0 || blueTeamIds == null || blueTeamIds.length == 0) {
                callback.onFailure(ErrorCode.DAO_CREATE_FAILED, "Both redTeamIds and blueTeamIds are required.");
                return;
            }

            ensureUniqueAllianceTeams(redTeamIds, blueTeamIds);

            String matchCode = buildMatchCode(matchType, matchNumber);
            String matchId = matchCode;

            Match existingMatch = LocalRepository.matchDao().getMatchById(matchId);
            if (existingMatch != null) {
                callback.onFailure(ErrorCode.DAO_CREATE_FAILED, "Match already exists: " + matchId);
                return;
            }

            Match match = new Match();
            match.setMatchType(matchType);
            match.setMatchNumber(matchNumber);
            match.setFieldNumber(fieldNumber > 0 ? fieldNumber : 1);
            match.setMatchStartTime(matchStartTime);
            match.setMatchCode(matchCode);
            match.setId(matchId);

            String redAllianceId = matchId + "_R";
            String blueAllianceId = matchId + "_B";

            clearAllianceData(redAllianceId, blueAllianceId);

            insertAllianceTeams(redAllianceId, redTeamIds);
            insertAllianceTeams(blueAllianceId, blueTeamIds);

            Score redScore = ScoreHandler.factoryScore();
            redScore.setAllianceId(redAllianceId);
            Score blueScore = ScoreHandler.factoryScore();
            blueScore.setAllianceId(blueAllianceId);

            LocalRepository.matchDao().insertMatch(match);
            LocalRepository.scoreDao().insertScore(redScore);
            LocalRepository.scoreDao().insertScore(blueScore);

            callback.onSuccess(match, "Match created successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_CREATE_FAILED, "Failed to create match: " + e.getMessage());
        }
    }

    public void updateMatch(Match match, RequestCallback<Match> callback) {
        try {
            if (!isEventReady()) {
                callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "No active event database.");
                return;
            }

            if (match == null || isBlank(match.getId())) {
                callback.onFailure(ErrorCode.DAO_UPDATE_FAILED, "Match id is required.");
                return;
            }

            LocalRepository.matchDao().updateMatch(match);
            callback.onSuccess(match, "Match updated successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_UPDATE_FAILED, "Failed to update match: " + e.getMessage());
        }
    }

    public void deleteMatch(String matchId, RequestCallback<Void> callback) {
        try {
            if (!isEventReady()) {
                callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "No active event database.");
                return;
            }

            if (isBlank(matchId)) {
                callback.onFailure(ErrorCode.DAO_DELETE_FAILED, "Match id is required.");
                return;
            }

            LocalRepository.matchDao().deleteMatch(matchId);

            String redAllianceId = matchId + "_R";
            String blueAllianceId = matchId + "_B";

            LocalRepository.allianceTeamDao().deleteAllianceTeamsByAllianceId(redAllianceId);
            LocalRepository.allianceTeamDao().deleteAllianceTeamsByAllianceId(blueAllianceId);
            LocalRepository.scoreDao().deleteScore(redAllianceId);
            LocalRepository.scoreDao().deleteScore(blueAllianceId);

            callback.onSuccess(null, "Match deleted successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_DELETE_FAILED, "Failed to delete match: " + e.getMessage());
        }
    }

    public void listMatches(RequestCallback<Match[]> callback) {
        try {
            if (!isEventReady()) {
                callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "No active event database.");
                return;
            }

            Match[] matches = LocalRepository.matchDao().listMatches();
            callback.onSuccess(matches, "Matches retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Failed to list matches: " + e.getMessage());
        }
    }

    public void getMatch(String matchId, RequestCallback<Match> callback) {
        try {
            if (!isEventReady()) {
                callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "No active event database.");
                return;
            }

            Match match = LocalRepository.matchDao().getMatchById(matchId);
            if (match == null) {
                callback.onFailure(ErrorCode.DAO_NOT_FOUND, "Match not found.");
                return;
            }
            callback.onSuccess(match, "Match retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Failed to get match: " + e.getMessage());
        }
    }

    public void listMatchesByType(int matchType, RequestCallback<Match[]> callback) {
        try {
            if (!isEventReady()) {
                callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "No active event database.");
                return;
            }

            Match[] matches = LocalRepository.matchDao().getMatchesByType(matchType);
            callback.onSuccess(matches, "Matches retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Failed to list matches: " + e.getMessage());
        }
    }

    public void getMatchDetail(String matchId, boolean withScore, RequestCallback<MatchDetailDto> callback) {
        try {
            if (!isEventReady()) {
                callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "No active event database.");
                return;
            }

            Match match = LocalRepository.matchDao().getMatchById(matchId);
            if (match == null) {
                callback.onFailure(ErrorCode.DAO_NOT_FOUND, "Match not found.");
                return;
            }

            MatchDetailDto detail = buildMatchDetail(match, withScore);
            callback.onSuccess(detail, "Match detail retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Failed to get match detail: " + e.getMessage());
        }
    }

    public void listMatchDetailsByType(int matchType, boolean withScore, RequestCallback<MatchDetailDto[]> callback) {
        try {
            if (!isEventReady()) {
                callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "No active event database.");
                return;
            }

            Match[] matches = LocalRepository.matchDao().getMatchesByType(matchType);
            List<MatchDetailDto> details = new ArrayList<>();
            if (matches != null) {
                for (Match match : matches) {
                    details.add(buildMatchDetail(match, withScore));
                }
            }

            callback.onSuccess(details.toArray(new MatchDetailDto[0]), "Match details retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Failed to list match details: " + e.getMessage());
        }
    }

    private MatchDetailDto buildMatchDetail(Match match, boolean withScore) throws Exception {
        String matchId = match.getId();
        AllianceTeam[] redAlliance = LocalRepository.allianceTeamDao().getAllianceTeamsByAllianceId(matchId + "_R");
        AllianceTeam[] blueAlliance = LocalRepository.allianceTeamDao().getAllianceTeamsByAllianceId(matchId + "_B");

        Team[] redTeams = resolveTeams(redAlliance);
        Team[] blueTeams = resolveTeams(blueAlliance);

        Score redScore = withScore ? LocalRepository.scoreDao().getScoreById(matchId + "_R") : null;
        Score blueScore = withScore ? LocalRepository.scoreDao().getScoreById(matchId + "_B") : null;

        MatchDetailDto detail = new MatchDetailDto();
        detail.setMatch(match);
        detail.setRedAllianceTeams(redAlliance);
        detail.setBlueAllianceTeams(blueAlliance);
        detail.setRedTeams(redTeams);
        detail.setBlueTeams(blueTeams);
        detail.setRedScore(redScore);
        detail.setBlueScore(blueScore);
        detail.setSurrogateMap(buildSurrogateMap(redAlliance, blueAlliance));
        return detail;
    }

    private Team[] resolveTeams(AllianceTeam[] allianceTeams) throws Exception {
        if (allianceTeams == null || allianceTeams.length == 0) {
            return new Team[0];
        }

        List<Team> teams = new ArrayList<>();
        for (AllianceTeam allianceTeam : allianceTeams) {
            Team team = LocalRepository.teamDao().getTeamById(allianceTeam.getTeamId());
            if (team != null) {
                teams.add(team);
            }
        }
        return teams.toArray(new Team[0]);
    }

    private Map<String, Boolean> buildSurrogateMap(AllianceTeam[] redAlliance, AllianceTeam[] blueAlliance) {
        Map<String, Boolean> map = new HashMap<>();
        addSurrogateEntries(map, redAlliance);
        addSurrogateEntries(map, blueAlliance);
        return map;
    }

    private void addSurrogateEntries(Map<String, Boolean> map, AllianceTeam[] allianceTeams) {
        if (allianceTeams == null) {
            return;
        }
        for (AllianceTeam allianceTeam : allianceTeams) {
            map.put(allianceTeam.getTeamId(), allianceTeam.isSurrogate());
        }
    }

    private void insertAllianceTeams(String allianceId, String[] teamIds) throws Exception {
        for (String teamId : teamIds) {
            if (isBlank(teamId)) {
                continue;
            }
            AllianceTeam team = new AllianceTeam();
            team.setAllianceId(allianceId);
            team.setTeamId(teamId.trim());
            team.setSurrogate(false);
            LocalRepository.allianceTeamDao().insertAllianceTeam(team);
        }
    }

    private void clearAllianceData(String redAllianceId, String blueAllianceId) throws Exception {
        LocalRepository.allianceTeamDao().deleteAllianceTeamsByAllianceId(redAllianceId);
        LocalRepository.allianceTeamDao().deleteAllianceTeamsByAllianceId(blueAllianceId);
        LocalRepository.scoreDao().deleteScore(redAllianceId);
        LocalRepository.scoreDao().deleteScore(blueAllianceId);
    }

    private void ensureUniqueAllianceTeams(String[] redTeamIds, String[] blueTeamIds) throws Exception {
        Set<String> red = new HashSet<>();
        for (String teamId : redTeamIds) {
            if (isBlank(teamId)) {
                continue;
            }
            String normalized = teamId.trim();
            if (!red.add(normalized)) {
                throw new Exception("Duplicate team ID in red alliance: " + normalized);
            }
        }

        Set<String> blue = new HashSet<>();
        for (String teamId : blueTeamIds) {
            if (isBlank(teamId)) {
                continue;
            }
            String normalized = teamId.trim();
            if (!blue.add(normalized)) {
                throw new Exception("Duplicate team ID in blue alliance: " + normalized);
            }
        }
    }

    private String buildMatchCode(int matchType, int matchNumber) {
        String prefix = switch (matchType) {
            case MatchType.QUALIFICATION -> "Q";
            case MatchType.PLAYOFF -> "P";
            case MatchType.SEMIFINAL -> "SF";
            case MatchType.FINAL -> "F";
            default -> "";
        };
        return prefix + matchNumber;
    }

    private boolean isEventReady() {
        return LocalRepository.eventDatabase() != null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
