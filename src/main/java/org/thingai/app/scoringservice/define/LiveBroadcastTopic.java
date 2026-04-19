package org.thingai.app.scoringservice.define;

public class LiveBroadcastTopic {
    public static final String BASE_TOPIC = "/live/";

    public static final String LIVE_MATCH = BASE_TOPIC + "match";
    public static final String LIVE_SCORE_UPDATE_RED = BASE_TOPIC + "score/red";
    public static final String LIVE_SCORE_UPDATE_BLUE = BASE_TOPIC + "score/blue";
    public static final String LIVE_DISPLAY_SCORE = BASE_TOPIC + "display/score";
    public static final String LIVE_DISPLAY_CONTROL = BASE_TOPIC + "display/control";
    public static final String LIVE_DISPLAY_RANKING = BASE_TOPIC + "display/ranking";
}
