package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.base.dao.Dao;

public class ScoreRepository {
    private static Dao dao;

    public static void initialize(Dao daoInstance) {
        dao = daoInstance;
    }

    public static Score insertScore(Score score) throws Exception {
        dao.insert(score);
        return score;
    }

    public static Score updateScore(Score score) throws Exception {
        dao.insertOrUpdate(score);
        return score;
    }

    public static void deleteScore(String scoreId) throws Exception {
        dao.deleteByColumn(Score.class, "id", scoreId);
    }

    public static void deleteAllScores() throws Exception {
        dao.deleteAll(Score.class);
    }

    public static Score[] listScores() throws Exception {
        return dao.readAll(Score.class);
    }

    public static Score getScoreById(String scoreId) throws Exception {
        Score[] scores = dao.query(Score.class, new String[]{"id"}, new String[]{scoreId});
        if (scores != null && scores.length > 0) {
            return scores[0];
        }
        return null;
    }
}