package org.thingai.app.scoringservice.strategy;

import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.entity.ScoreDefine;

import java.util.HashMap;

public interface IScoreStrategy<T extends Score> {
    void calculateTotalScore(); // return as int
    void calculatePenalties(); // return as int
    void fromJson(String json);
    String getRawScoreData();
    HashMap<String, ScoreDefine> getScoreDefinitions();
}
