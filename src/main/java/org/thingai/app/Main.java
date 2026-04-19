package org.thingai.app;

import eventimpl.fanroc.FanrocRankingStrategy;
import eventimpl.fanroc.FanrocScore;
import io.javalin.Javalin;
import org.thingai.app.api.ApiServer;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.base.log.ILog;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Entry point. Builds the Javalin API, boots the scoring service, and
 * registers the season-specific scoring/ranking strategies.
 *
 * <p>Historically this class was annotated {@code @SpringBootApplication};
 * during the migration to Javalin the Spring container was removed. The
 * lifecycle is now explicit:
 * <ol>
 *   <li>Construct and initialize {@link ScoringService} (databases, handlers).</li>
 *   <li>Register the current season's scoring/ranking implementations.</li>
 *   <li>Build the Javalin server and start it on the first available port.</li>
 * </ol>
 */
public class Main {

    private static final String TAG = "Main";

    public static void main(String[] args) {
        // 1. Bring up the scoring domain (DB, handlers, state manager, etc.).
        ScoringService scoringService = new ScoringService();
        scoringService.init();
        scoringService.registerScoreClass(FanrocScore.class);
        scoringService.registerRankingStrategy(new FanrocRankingStrategy());

        // 2. Build and start the HTTP API.
        Javalin app = ApiServer.build();
        int port = ApiServer.choosePort();
        app.start("0.0.0.0", port);

        ILog.i(TAG, "Service running on URL: http://" + getIpAddress() + ":" + port);

        // Graceful shutdown so Jetty releases the port on Ctrl+C.
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop, "javalin-shutdown"));
    }

    private static String getIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            ILog.e(TAG, "Failed to get IP address: " + e.getMessage());
            return "localhost";
        }
    }
}
