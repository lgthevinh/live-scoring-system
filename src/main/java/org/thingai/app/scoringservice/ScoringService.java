package org.thingai.app.scoringservice;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.thingai.app.scoringservice.entity.config.AccountRole;
import org.thingai.app.scoringservice.entity.config.AuthData;
import org.thingai.app.scoringservice.entity.config.DbMapEntity;
import org.thingai.app.scoringservice.entity.event.Event;
import org.thingai.app.scoringservice.entity.ranking.IRankingStrategy;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.handler.*;
import org.thingai.app.scoringservice.repository.*;
import org.thingai.base.Service;
import org.thingai.base.dao.Dao;
import org.thingai.base.log.ILog;
import org.thingai.platform.dao.DaoFile;
import org.thingai.platform.dao.DaoSqlite;
import org.thingai.platform.log.ILogImpl;

public class ScoringService extends Service {
    private static final String SERVICE_NAME = "ScoringService";

    private static EventHandler eventHandler;
    private static AuthHandler authHandler;
    private static ScoringHandler scoringHandler;
    private static ScheduleHandler scheduleHandler;
    private static RankingHandler rankingHandler;
    private static BroadcastHandler broadcastHandler;
    private static LiveScoreHandler liveScoreHandler;

    public ScoringService() {
        super();
        ILog.ENABLE_LOGGING = true;
        ILog.logLevel = ILog.INFO;
    }

    @Override
    protected void onServiceInit() {
        new ILogImpl(appDir, true);
        Dao dao = new DaoSqlite(appDir + "/scoring_system.db");

        ILog.i(SERVICE_NAME, "Initializing ScoringService with app directory: " + appDir);

        dao.initDao(new Class[]{
                Event.class,

                // System entities
                AuthData.class,
                AccountRole.class,
                DbMapEntity.class
        });
        // Initialize system level repositories and handlers
        AuthRepository.initialize(dao);
        EventRepository.initialize(dao);

        authHandler = new AuthHandler();
        eventHandler = new EventHandler(dao, new EventHandler.EventCallback() {
            @Override
            public void onSetEvent(Dao eventDao, DaoFile eventDaoFile) {
                ILog.i(SERVICE_NAME, "Event is set. Injecting handlers with new event data.");
                injectDao(eventDao, eventDaoFile);
            }

            @Override
            public void isCurrentEventSet(Event currentEvent, Dao eventDao, DaoFile eventDaoFile) {
                ILog.i(SERVICE_NAME, "Current event is set to: ", currentEvent.getEventCode());
                injectDao(eventDao, eventDaoFile);
            }

            @Override
            public void isNotCurrentEventSet() {
                ILog.w(SERVICE_NAME, "No current event is set.");
            }
        });

        ILog.i(SERVICE_NAME, "ScoringService initialized. version: " + version);
        ILog.i(SERVICE_NAME, "Database initialized at: " + appDir + "/scoring_system.db");
        ILog.i(SERVICE_NAME, "File storage initialized at: " + appDir + "/files");
    }

    public static AuthHandler authHandler() {
        return authHandler;
    }

    public static EventHandler eventHandler() {
        return eventHandler;
    }

    public static ScoringHandler scoreHandler() {
        return scoringHandler;
    }

    public static ScheduleHandler matchHandler() {
        return scheduleHandler;
    }

    public static BroadcastHandler broadcastHandler() {
        return broadcastHandler;
    }

    public static LiveScoreHandler liveScoreHandler() {
        return liveScoreHandler;
    }

    public static RankingHandler rankingHandler() {
        return rankingHandler;
    }

    public void setSimpMessagingTemplate(SimpMessagingTemplate simpMessagingTemplate) {
        broadcastHandler = new BroadcastHandler(simpMessagingTemplate);
        ILog.d("ScoringService::setSimpMessagingTemplate", broadcastHandler().toString());
    }

    public void registerScoreClass(Class<? extends Score> scoreClass) {
        ScoringHandler.setScoreClass(scoreClass);
    }

    public void registerRankingStrategy(IRankingStrategy rankingStrategy) {
        RankingHandler.setRankingStrategy(rankingStrategy);
    }

    private void injectDao(Dao dao, DaoFile daoFile) {
        AuthRepository.initialize(dao);
        TeamRepository.initialize(dao);
        MatchRepository.initialize(dao);
        AllianceTeamRepository.initialize(dao);
        ScoreRepository.initialize(dao);
        RankEntryRepository.initialize(dao);

        scheduleHandler = new ScheduleHandler();
        scoringHandler = new ScoringHandler(dao, daoFile);
        rankingHandler = new RankingHandler(dao, scheduleHandler);

        liveScoreHandler = new LiveScoreHandler(scheduleHandler, scoringHandler, rankingHandler);
        liveScoreHandler.setBroadcastHandler(broadcastHandler);
    }
}