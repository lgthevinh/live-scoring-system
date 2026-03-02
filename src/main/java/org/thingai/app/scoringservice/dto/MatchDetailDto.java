package org.thingai.app.scoringservice.dto;

import org.thingai.app.scoringservice.entity.match.AllianceTeam;
import org.thingai.app.scoringservice.entity.match.Match;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.entity.team.Team;

public class MatchDetailDto {
    private Match match;
    private AllianceTeam[] redAllianceTeams;
    private AllianceTeam[] blueAllianceTeams;
    private Team[] redTeams;
    private Team[] blueTeams;
    private Score redScore;
    private Score blueScore;

    public MatchDetailDto() {
    }

    public MatchDetailDto(Match match, AllianceTeam[] redAllianceTeams, AllianceTeam[] blueAllianceTeams, Team[] redTeams, Team[] blueTeams) {
        this.match = match;
        this.redTeams = redTeams;
        this.blueTeams = blueTeams;
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

    public AllianceTeam[] getRedAllianceTeams() {
        return redAllianceTeams;
    }

    public void setRedAllianceTeams(AllianceTeam[] redAllianceTeams) {
        this.redAllianceTeams = redAllianceTeams;
    }

    public AllianceTeam[] getBlueAllianceTeams() {
        return blueAllianceTeams;
    }

    public void setBlueAllianceTeams(AllianceTeam[] blueAllianceTeams) {
        this.blueAllianceTeams = blueAllianceTeams;
    }
}
