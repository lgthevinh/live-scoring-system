package org.thingai.app.scoringservice.entity;

public class RankingSnapshot {
    private int matchType;

    private RankingEntry[] rankingEntries;

    public int getMatchType() {
        return matchType;
    }

    public void setMatchType(int matchType) {
        this.matchType = matchType;
    }

    public RankingEntry[] getRankingEntries() {
        return rankingEntries;
    }

    public void setRankingEntries(RankingEntry[] rankingEntries) {
        this.rankingEntries = rankingEntries;
    }
}
