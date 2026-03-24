package org.thingai.app.scoringservice;

import org.thingai.app.scoringservice.callback.EventHandlerCallback;
import org.thingai.app.scoringservice.entity.Event;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.handler.*;
import org.thingai.app.scoringservice.matchcontrol.MatchControl;
import org.thingai.app.scoringservice.matchcontrol.StateManager;
import org.thingai.app.scoringservice.repository.LocalRepository;
import org.thingai.app.scoringservice.service.MatchMakerService;
import org.thingai.app.scoringservice.strategy.IRankingStrategy;
import org.thingai.base.Service;
import org.thingai.base.log.ILog;
import org.thingai.platform.log.ILogImpl;

public class ScoringService extends Service {
    private static final String SERVICE_NAME = "ScoringService";

    private static EventHandler eventHandler;
    private static AuthHandler authHandler;
    private static TeamHandler teamHandler;
    private static ScoringHandler scoringHandler;
    private static ScheduleHandler scheduleHandler;
    private static MatchHandler matchHandler;
    private static RankingHandler rankingHandler;

    private static StateManager stateManager;
    private static MatchControl matchControl;

    public ScoringService() {
        super();
        ILog.ENABLE_LOGGING = true;
        ILog.logLevel = ILog.INFO;
    }

    public static MatchHandler matchHandler() {
        return matchHandler;
    }

    public static AuthHandler authHandler() {
        return authHandler;
    }

    public static EventHandler eventHandler() {
        return eventHandler;
    }

    public static TeamHandler teamHandler() {
        return teamHandler;
    }

    public static ScheduleHandler scheduleHandler() {
        return scheduleHandler;
    }

    @Override
    protected void onServiceInit() {
        new ILogImpl(appDir, true);
        ILog.i(SERVICE_NAME, "Initializing ScoringService with app directory: " + appDir);

        LocalRepository.initializeSystem(appDir + "/scoring_system.db");

        authHandler = new AuthHandler();
        eventHandler = new EventHandler(new EventHandlerCallback() {
            @Override
            public void onSetEvent() {
                ILog.i(SERVICE_NAME, "Event is set. Injecting handlers with new event data.");
            }

            @Override
            public void isCurrentEventSet(Event currentEvent) {
                ILog.i(SERVICE_NAME, "Current event is set to: ", currentEvent.getEventCode());
            }

            @Override
            public void isNotCurrentEventSet() {
                ILog.w(SERVICE_NAME, "No current event is set.");
            }
        });
        teamHandler = new TeamHandler();
        scheduleHandler = new ScheduleHandler(new MatchMakerService());
        matchHandler = new MatchHandler();
        scoringHandler = new ScoringHandler();
        rankingHandler = new RankingHandler();

        stateManager = new StateManager();
        matchControl = new MatchControl(stateManager);

        ILog.i(SERVICE_NAME, "ScoringService initialized. version: " + version);
        ILog.i(SERVICE_NAME, "Database initialized at: " + appDir + "/scoring_system.db");
        ILog.i(SERVICE_NAME, "File storage initialized at: " + appDir + "/files");
    }

    public static ScoringHandler scoreHandler() {
        return scoringHandler;
    }

    public static RankingHandler rankingHandler() {
        return rankingHandler;
    }

    public static StateManager stateManager() {
        return stateManager;
    }

    public static MatchControl matchControl() {
        return matchControl;
    }

    public void registerScoreClass(Class<? extends Score> scoreClass) {
        ScoringHandler.setScoreClass(scoreClass);
    }

    public void registerRankingStrategy(IRankingStrategy rankingStrategy) {
        RankingHandler.setRankingStrategy(rankingStrategy);
    }
}
