package org.thingai.app.scoringservice;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.thingai.app.scoringservice.entity.config.AccountRole;
import org.thingai.app.scoringservice.entity.match.AllianceTeam;
import org.thingai.app.scoringservice.entity.ranking.IRankingStrategy;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.handler.BroadcastHandler;
import org.thingai.app.scoringservice.handler.LiveScoreHandler;
import org.thingai.app.scoringservice.handler.entityhandler.*;
import org.thingai.base.Service;
import org.thingai.base.cache.LRUCache;
import org.thingai.base.dao.Dao;
import org.thingai.app.scoringservice.entity.event.Event;
import org.thingai.app.scoringservice.entity.team.Team;
import org.thingai.app.scoringservice.entity.config.AuthData;
import org.thingai.app.scoringservice.entity.config.DbMapEntity;
import org.thingai.app.scoringservice.entity.match.Match;
import org.thingai.base.log.ILog;
import org.thingai.platform.dao.DaoFile;
import org.thingai.platform.dao.DaoSqlite;

import java.util.HashMap;

public class ScoringService extends Service {
    private static final String SERVICE_NAME = "ScoringService";

    private final LRUCache<String, Match> matchCache = new LRUCache<>(50, new HashMap<>());
    private final LRUCache<String, AllianceTeam[]> allianceTeamCache = new LRUCache<>(100, new HashMap<>());
    private final LRUCache<String, Team> teamCache = new LRUCache<>(30, new HashMap<>());

    private static AuthHandler authHandler;
    private static EventHandler eventHandler;
    private static TeamHandler teamHandler;
    private static ScoreHandler scoreHandler;
    private static MatchHandler matchHandler;
    private static RankingHandler rankingHandler;
    private static BroadcastHandler broadcastHandler;
    private static LiveScoreHandler liveScoreHandler;

    @Override
    protected void onServiceInit() {
        Dao dao = new DaoSqlite(appDir + "/scoring_system.db");

        System.out.println("Service initialized with app directory: " + appDir);

        dao.initDao(new Class[]{
                Event.class,

                // System entities
                AuthData.class,
                AccountRole.class,
                DbMapEntity.class
        });
        // Initialize handler
        authHandler = new AuthHandler(dao);
        eventHandler = new EventHandler(dao, new EventHandler.EventCallback() {
            @Override
            public void onSetEvent(Dao eventDao, DaoFile eventDaoFile) {
                ILog.i(SERVICE_NAME, "Event is set. Injecting handlers with new event data.");
                injectHandler(eventDao, eventDaoFile);
            }

            @Override
            public void isCurrentEventSet(Event currentEvent, Dao eventDao, DaoFile eventDaoFile) {
                ILog.i(SERVICE_NAME, "Current event is set to: ", currentEvent.getEventCode());
                injectHandler(eventDao, eventDaoFile);
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

    public static TeamHandler teamHandler() {
        return teamHandler;
    }

    public static ScoreHandler scoreHandler() {
        return scoreHandler;
    }

    public static MatchHandler matchHandler() {
        return matchHandler;
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
        ScoreHandler.setScoreClass(scoreClass);
    }

    public void registerRankingStrategy(IRankingStrategy rankingStrategy) {
        RankingHandler.setRankingStrategy(rankingStrategy);
    }

    private void injectHandler(Dao dao, DaoFile daoFile) {
        teamHandler = new TeamHandler(dao);
        matchHandler = new MatchHandler(dao);
        scoreHandler = new ScoreHandler(dao, daoFile);
        rankingHandler = new RankingHandler(dao, matchHandler);

        liveScoreHandler = new LiveScoreHandler(matchHandler, scoreHandler, rankingHandler);
        liveScoreHandler.setBroadcastHandler(broadcastHandler);

    }
}
