package org.thingai.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.base.log.ILog;
import org.thingai.app.fanroc.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        ScoringService scoringService = new ScoringService();
        scoringService.name = "Scoring System";
        scoringService.appDirName = "scoring_system";
        scoringService.version = "1.0.0";

        ILog.ENABLE_LOGGING = true;
        ILog.logLevel = ILog.INFO;

        // 1. Start Spring and get its application context
        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);

        // --- THIS IS THE BRIDGE ---
        // 2. Get the working BroadcastController that Spring created.
        SimpMessagingTemplate simpMessagingTemplate = context.getBean(SimpMessagingTemplate.class);

        scoringService.setSimpMessagingTemplate(simpMessagingTemplate);
        scoringService.init();

        scoringService.registerScoreClass(FanrocScore.class); // Register the scoring class for the season specific logic
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
