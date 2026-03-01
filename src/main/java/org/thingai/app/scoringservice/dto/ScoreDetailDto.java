package org.thingai.app.scoringservice.dto;

import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.handler.ScoreHandler;

public class ScoreDetailDto {
    private Score baseScore;
    private Object detailScore;

    public ScoreDetailDto() {
        baseScore = ScoreHandler.factoryScore();
    }

    public void setScore(String allianceId) {

    }

    public Score getScore() {
        return baseScore;
    }
}
