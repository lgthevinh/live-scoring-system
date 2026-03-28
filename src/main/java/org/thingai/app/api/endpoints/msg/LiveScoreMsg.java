package org.thingai.app.api.endpoints.msg;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.thingai.app.scoringservice.ScoringService;

@Controller
public class LiveScoreMsg {

    @MessageMapping("live/score/update/blue")
    public void handleLiveScoreUpdateBlue(@Payload String jsonUpdate) {
        ScoringService.liveScoreControl().handleLiveScoreUpdate(jsonUpdate, "blue");
    }

    @MessageMapping("live/score/update/red")
    public void handleLiveScoreUpdateRed(@Payload String jsonUpdate) {
        ScoringService.liveScoreControl().handleLiveScoreUpdate(jsonUpdate, "red");
    }

}
