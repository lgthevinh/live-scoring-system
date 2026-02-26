import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.MatchType;
import org.thingai.app.scoringservice.entity.match.AllianceTeam;
import org.thingai.app.scoringservice.entity.match.Match;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.entity.team.Team;
import org.thingai.app.scoringservice.entity.time.TimeBlock;
import org.thingai.app.scoringservice.handler.entityhandler.MatchHandler;
import org.thingai.base.dao.Dao;
import org.thingai.platform.dao.DaoFile;
import org.thingai.platform.dao.DaoSqlite;

public class TestMatchHandler {
    private static MatchHandler matchHandler;

    @BeforeAll
    public static void setup() {
        // Set up the DAO factory with SQLite configuration
        String url = "src/test/resources/scoring_system.db";
        Dao dao = new DaoSqlite(url);
        dao.initDao(new Class[] {
            Score.class,
            Match.class,
            AllianceTeam.class,
            Team.class
        }); // Ensure the DAO is ready for use

        DaoFile daoFile = new DaoFile("src/test/resources/files");
        matchHandler = new MatchHandler(dao);
    }

    @Test
    public void testCreateMatch() {
        int matchType = MatchType.QUALIFICATION;
        int matchNumber = 1;
        String matchStartTime = "2024-10-01T10:00:00Z";
        String[] redTeamIds = new String[] {"2231", "2323", "23423"};
        String[] blueTeamIds = new String[] {"2541", "1323", "23483"};

        matchHandler.createMatch(matchType, matchNumber, matchStartTime, redTeamIds, blueTeamIds, new RequestCallback<Match>() {
            @Override
            public void onSuccess(Match responseObject, String message) {
                System.out.println("Match created successfully: " + responseObject);
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                System.err.println("Failed to create match: " + errorMessage);
            }
        });

    }

    @Test
    public void testGenerateMatchSchedule() {
        int rounds = 5;
        System.out.println("Generating match schedule for " + rounds + " rounds.");
        matchHandler.generateMatchScheduleV2(rounds, "11", 5,1, new TimeBlock[]{}, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void responseObject, String message) {
                System.out.println("Match schedule generated successfully: " + responseObject);
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                System.err.println("Failed to generate match schedule: " + errorMessage);
            }
        });
    }
}
