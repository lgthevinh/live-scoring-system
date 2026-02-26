import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.handler.entityhandler.ScoreHandler;
import org.thingai.app.fanroc.FanrocScore;
import org.thingai.base.dao.Dao;
import org.thingai.platform.dao.DaoFile;
import org.thingai.platform.dao.DaoSqlite;

public class TestScoreHandler {
    private static ScoreHandler scoreHandler;

    @BeforeAll
    public static void setup() {
        // Set up the DAO factory with SQLite configuration
        String url = "src/test/resources/test.db";
        Dao dao = new DaoSqlite(url);
        dao.initDao(new Class[] {
            Score.class,
        }); // Ensure the DAO is ready for use

        DaoFile daoFile = new DaoFile("src/test/resources/files");
        scoreHandler = new ScoreHandler(dao, daoFile);
        scoreHandler.setScoreClass(FanrocScore.class);
    }

    @Test
    public void testCreateAndSaveScore() {
        Score score = getScore();

        scoreHandler.updateAndSaveScore(score, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void result, String message) {
                System.out.println("Score saved successfully: " + message);
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                System.err.println("Failed to save score. Error " + errorCode + ": " + errorMessage);
            }
        });

    }

    private static Score getScore() {
        String jsonRawScoreData = """
                {
                    "robotHanged": 1,
                    "robotParked": 2,
                    "ballEntered": 20,
                    "minorFault": 2,
                    "majorFault": 1
                }
                """;

        Score score = ScoreHandler.factoryScore();
        score.setAllianceId("Q1_B");
        score.fromJson(jsonRawScoreData);
        score.calculatePenalties();
        score.calculateTotalScore();
        return score;
    }


}
