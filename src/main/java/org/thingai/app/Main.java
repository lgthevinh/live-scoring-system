package org.thingai.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.base.log.ILog;
import org.thingai.app.fanroc.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
public class Main {

    private static final String DEFAULT_VERSION = "1.6";

    public static void main(String[] args) {
        ScoringService scoringService = new ScoringService();
        scoringService.name = "Scoring System";
        scoringService.appDirName = "scoring_system";
        scoringService.version = DEFAULT_VERSION;

        ILog.ENABLE_LOGGING = true;
        ILog.logLevel = ILog.INFO;

        // 1. Start Spring and get its application context
        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);

        // --- THIS IS THE BRIDGE ---
        // 2. Get the working BroadcastController that Spring created.
        SimpMessagingTemplate simpMessagingTemplate = context.getBean(SimpMessagingTemplate.class);

        scoringService.setSimpMessagingTemplate(simpMessagingTemplate);
        
        // Register scoring classes BEFORE init so they're available when handlers are created
        scoringService.registerScoreClass(FanrocScore.class);
        scoringService.registerRankingStrategy(new FanrocRankingStrategy());
        
        scoringService.init();
        ILog.i("Main", "Service v" + DEFAULT_VERSION + " running on URL:" + " http://" + getIpAddress() + ":" + getActualPort(context));
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
