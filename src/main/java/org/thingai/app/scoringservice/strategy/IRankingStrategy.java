package org.thingai.app.scoringservice.strategy;

import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.entity.RankingEntry;
import org.thingai.app.scoringservice.entity.RankingStat;

public interface IRankingStrategy {
    RankingEntry[] sortRankingEntries(RankingEntry[] entries);
    RankingStat[] setRankingStat(MatchDetailDto matchDetailDto, Score blueScore, Score redScore);
}
