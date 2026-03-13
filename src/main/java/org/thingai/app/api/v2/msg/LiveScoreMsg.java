package org.thingai.app.api.v2.msg;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class LiveScoreMsg {

    @MessageMapping("live/score/blue")
    public void handleLiveScoreUpdateBlue(@Payload String jsonUpdate) {
    }

    @MessageMapping("live/score/red")
    public void handleLiveScoreUpdateRed(@Payload String jsonUpdate) {
    }

}
