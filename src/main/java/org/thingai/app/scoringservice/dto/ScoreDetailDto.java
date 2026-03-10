package org.thingai.app.scoringservice.dto;

import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.handler.ScoringHandler;

public class ScoreDetailDto {
    private Score baseScore;
    private Object detailScore;

    public ScoreDetailDto() {
        baseScore = ScoringHandler.factoryScore();
    }

    public void setScore(String allianceId) {

    }

    public Score getScore() {
        return baseScore;
    }
}
