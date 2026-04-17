package org.thingai.app.scoringservice.dto;

import com.google.gson.Gson;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.handler.ScoreHandler;

import java.util.Map;

public class ScoreDetailDto {
    private static final Gson GSON = new Gson();
    private Score baseScore;
    private Object detailScore;

    public ScoreDetailDto() {
        baseScore = ScoreHandler.factoryScore();
    }

    public ScoreDetailDto(Score score) {
        setScore(score);
    }

    public void setScore(String allianceId) {
        if (baseScore == null) {
            baseScore = ScoreHandler.factoryScore();
        }
        baseScore.setAllianceId(allianceId);
    }

    public void setScore(Score score) {
        this.baseScore = score;
        this.detailScore = parseDetailScore(score);
    }

    public Score getScore() {
        return baseScore;
    }

    public Object getDetailScore() {
        return detailScore;
    }

    private Object parseDetailScore(Score score) {
        if (score == null || score.getRawScoreData() == null || score.getRawScoreData().isBlank()) {
            return null;
        }
        try {
            return GSON.fromJson(score.getRawScoreData(), Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
