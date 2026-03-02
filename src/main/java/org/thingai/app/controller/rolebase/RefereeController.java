package org.thingai.app.controller.rolebase;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.controller.utils.ResponseEntityUtil;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import java.util.concurrent.CompletableFuture;

@Controller
@RequestMapping("/api/ref")
public class RefereeController {

    @PostMapping("/submit/{color}/{allianceId}/final-score")
    public ResponseEntity<Object> submitFinalScore(@PathVariable String color, @PathVariable String allianceId, @RequestBody String jsonScoreData) {
        if (color == null || color.isEmpty()) {
            return ResponseEntity.badRequest().body("Color parameter is required");
        }
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        boolean isRed = color.equalsIgnoreCase("red");
        ScoringService.liveScoreHandler().handleScoreSubmission(isRed, allianceId, jsonScoreData, new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                future.complete(ResponseEntity.ok().body(new ApiRefResponse(true, "Score submission successful")));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    static class ApiRefResponse {
        public boolean success;
        public String message;

        public ApiRefResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
