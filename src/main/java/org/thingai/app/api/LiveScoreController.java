package org.thingai.app.api;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class LiveScoreController {

    @MessageMapping("live/score/blue")
    public void handleLiveScoreUpdateBlue(@Payload String jsonUpdate) {
    }

    @MessageMapping("live/score/red")
    public void handleLiveScoreUpdateRed(@Payload String jsonUpdate) {
    }

}
