import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.entity.team.Team;
import org.thingai.app.scoringservice.handler.entityhandler.TeamHandler;
import org.thingai.base.dao.Dao;
import org.thingai.platform.dao.DaoSqlite;

public class TestTeamHandler {
    private static TeamHandler teamHandler;

    @BeforeAll
    public static void setup() {
        // Set up the DAO factory with SQLite configuration
        String url = "src/test/resources/test.db";
        Dao dao = new DaoSqlite(url);
        dao.initDao(new Class[] {
                Team.class,
        }); // Ensure the DAO is ready for use
        teamHandler = new TeamHandler(dao);
    }

    private Team[] generateRandomTeams(int count) {
        Team[] teams = new Team[count];
        for (int i = 0; i < count; i++) {
            Team team = new Team();
            team.setTeamId("team" + i);
            team.setTeamName("Team " + i);
            team.setTeamSchool("School " + i);
            team.setTeamRegion("Region " + (i % 5));
            teams[i] = team;
        }
        return teams;
    }

    @Test
    public void testAddTeams() {
        Team[] teams = generateRandomTeams(10);
        for (Team team : teams) {
            teamHandler.addTeam(team, new RequestCallback<Team>() {
                @Override
                public void onSuccess(Team responseObject, String message) {
                    System.out.println("Added team: " + responseObject.getTeamId() + " - " + message);
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    System.err.println("Failed to add team: " + errorMessage);
                }
            });
        }
    }

}
