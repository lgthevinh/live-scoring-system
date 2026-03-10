package org.thingai.app.demo;

import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.strategy.IRankingStrategy;
import org.thingai.app.scoringservice.entity.RankingEntry;
import org.thingai.app.scoringservice.entity.RankingStat;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.entity.Team;

import java.util.Arrays;

public class DefaultRankingStrategy implements IRankingStrategy {
    @Override
    public RankingEntry[] sortRankingEntries(RankingEntry[] entries) {
        // Sort by ranking points descending, then by total score descending, then by penalties ascending
        Arrays.sort(entries, (a, b) -> {
            if (b.getRankingPoints() != a.getRankingPoints()) {
                return Integer.compare(b.getRankingPoints(), a.getRankingPoints());
            } else if (b.getTotalScore() != a.getTotalScore()) {
                return Integer.compare(b.getTotalScore(), a.getTotalScore());
            } else {
                return Integer.compare(a.getTotalPenalties(), b.getTotalPenalties());
            }
        });
        return entries;
    }

    @Override
    public RankingStat[] setRankingStat(MatchDetailDto matchDetailDto, Score blueScore, Score redScore) {
        Team[] redTeams = matchDetailDto.getRedTeams();
        Team[] blueTeams = matchDetailDto.getBlueTeams();
        RankingStat[] stats = new RankingStat[redTeams.length + blueTeams.length];

        int index = 0;
        for (Team team : blueTeams) {
            RankingStat stat = new RankingStat();
            stat.setTeamId(team.getTeamId());
            stat.setScore(blueScore.getTotalScore());
            stat.setPenalties(blueScore.getPenaltiesScore());
            boolean isWin = blueScore.getTotalScore() > redScore.getTotalScore();
            stat.setWin(isWin);
            stat.setRankingPoints(isWin ? 3 : 1);
            stats[index++] = stat;
        }

        for (Team team : redTeams) {
            RankingStat stat = new RankingStat();
            stat.setTeamId(team.getTeamId());
            stat.setScore(redScore.getTotalScore());
            stat.setPenalties(redScore.getPenaltiesScore());
            boolean isWin = redScore.getTotalScore() > blueScore.getTotalScore();
            stat.setWin(isWin);
            stat.setRankingPoints(isWin ? 3 : 1);
            stats[index++] = stat;
        }

        return stats;
    }
}