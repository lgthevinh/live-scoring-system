package org.thingai.app.scoringservice.entity;

public class RankingStat {
    private String teamId;
    private int score;
    private int penalties;
    private int rankingPoints; // e.g., 3 for win, 1 for loss
    private boolean isWin;

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getPenalties() {
        return penalties;
    }

    public void setPenalties(int penalties) {
        this.penalties = penalties;
    }

    public int getRankingPoints() {
        return rankingPoints;
    }

    public void setRankingPoints(int rankingPoints) {
        this.rankingPoints = rankingPoints;
    }

    public boolean isWin() {
        return isWin;
    }

    public void setWin(boolean win) {
        isWin = win;
    }
}
