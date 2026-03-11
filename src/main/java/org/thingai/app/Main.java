package org.thingai.app;

import eventimpl.fanroc.FanrocRankingStrategy;
import eventimpl.fanroc.FanrocScore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.base.log.ILog;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        ScoringService scoringService = new ScoringService();
        scoringService.name = "Scoring System";
        scoringService.appDirName = "scoring_system";
        scoringService.version = "1.5";

        // 1. Start Spring and get its application context
        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);

        scoringService.init();
        scoringService.registerScoreClass(FanrocScore.class); // Register the scoring class for the season specific logic
        scoringService.registerRankingStrategy(new FanrocRankingStrategy());

        ILog.i("Main", "Service running on URL:" + " http://" + getIpAddress() + ":" + getActualPort(context));
    }

    private static int getActualPort(ConfigurableApplicationContext context) {
        try {
            if (context instanceof ServletWebServerApplicationContext serverContext) {
                return serverContext.getWebServer().getPort();
            }
        } catch (Exception e) {
            ILog.e("Main", "Failed to get server port: " + e.getMessage());
        }
        return -1; // Port not available
    }

    private static String getIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            ILog.e("Main", "Failed to get IP address: " + e.getMessage());
            return "localhost"; // Fallback to localhost
        }
    }
}
