package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.base.dao.Dao;

public class DaoScore {
    private final Dao dao;

    public DaoScore(Dao dao) {
        this.dao = dao;
    }

    public Score insertScore(Score score) throws Exception {
        dao.insert(score);
        return score;
    }

    public Score updateScore(Score score) throws Exception {
        dao.insertOrUpdate(score);
        return score;
    }

    public void deleteScore(String scoreId) throws Exception {
        dao.deleteByColumn(Score.class, "id", scoreId);
    }

    public void deleteAllScores() throws Exception {
        dao.deleteAll(Score.class);
    }

    public Score[] listScores() throws Exception {
        return dao.readAll(Score.class);
    }

    /**
     * Get a score by its Alliance ID.
     *
     * @param scoreId The Alliance ID of the score to retrieve. (Note: {@code matchId}_"R/B")
     * @return The Score object if found, or null if not found.
     * @throws Exception If there is an error during the database operation.
     */
    public Score getScoreById(String scoreId) throws Exception {
        Score[] scores = dao.query(Score.class, new String[]{"id"}, new String[]{scoreId});
        if (scores != null && scores.length > 0) {
            return scores[0];
        }
        return null;
    }
}
