package org.thingai.app.scoringservice.dto;

import org.thingai.app.scoringservice.entity.match.AllianceTeam;
import org.thingai.app.scoringservice.entity.match.Match;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.entity.team.Team;

import java.util.HashMap;

public class MatchDetailDto {
    private Match match;
    private Team[] redTeams;
    private Team[] blueTeams;
    private Score redScore;
    private Score blueScore;
    private String redTempScoreRawData;
    private String blueTempScoreRawData;
    private boolean hasRedTempScore;
    private boolean hasBlueTempScore;

    private HashMap<String, Boolean> surrogateMap;

    public MatchDetailDto() {
    }

    public MatchDetailDto(Match match, Team[] redTeams, Team[] blueTeams, HashMap<String, Boolean> surrogateMap) {
        this.match = match;
        this.redTeams = redTeams;
        this.blueTeams = blueTeams;
        this.surrogateMap = surrogateMap;
    }

    public MatchDetailDto(Match match, Team[] redTeams, Team[] blueTeams, Score redScore, Score blueScore, HashMap<String, Boolean> surrogateMap) {
        this.match = match;
        this.redTeams = redTeams;
        this.blueTeams = blueTeams;
        this.redScore = redScore;
        this.blueScore = blueScore;
        this.surrogateMap = surrogateMap;
    }

    public HashMap<String, Boolean> getSurrogateMap() {
        return surrogateMap;
    }

    public void setSurrogateMap(HashMap<String, Boolean> surrogateMap) {
        this.surrogateMap = surrogateMap;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public Team[] getRedTeams() {
        return redTeams;
    }

    public void setRedTeams(Team[] redTeams) {
        this.redTeams = redTeams;
    }

    public Team[] getBlueTeams() {
        return blueTeams;
    }

    public void setBlueTeams(Team[] blueTeams) {
        this.blueTeams = blueTeams;
    }

    public Score getRedScore() {
        return redScore;
    }

    public void setRedScore(Score redScore) {
        this.redScore = redScore;
    }

    public Score getBlueScore() {
        return blueScore;
    }

    public void setBlueScore(Score blueScore) {
        this.blueScore = blueScore;
    }

    public String getRedTempScoreRawData() {
        return redTempScoreRawData;
    }

    public void setRedTempScoreRawData(String redTempScoreRawData) {
        this.redTempScoreRawData = redTempScoreRawData;
    }

    public String getBlueTempScoreRawData() {
        return blueTempScoreRawData;
    }

    public void setBlueTempScoreRawData(String blueTempScoreRawData) {
        this.blueTempScoreRawData = blueTempScoreRawData;
    }

    public boolean isHasRedTempScore() {
        return hasRedTempScore;
    }

    public void setHasRedTempScore(boolean hasRedTempScore) {
        this.hasRedTempScore = hasRedTempScore;
    }

    public boolean isHasBlueTempScore() {
        return hasBlueTempScore;
    }

    public void setHasBlueTempScore(boolean hasBlueTempScore) {
        this.hasBlueTempScore = hasBlueTempScore;
    }
}
