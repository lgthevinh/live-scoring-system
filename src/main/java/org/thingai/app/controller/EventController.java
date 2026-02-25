package org.thingai.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.controller.utils.ResponseEntityUtil;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.entity.event.Event;
import org.thingai.app.scoringservice.define.ErrorCode;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/event")
public class EventController {

    @GetMapping("/")
    public ResponseEntity<Object> listEvents() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.eventHandler().listEvents(new RequestCallback<Event[]>() {
            @Override
            public void onSuccess(Event[] responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(500).body("Error listing events: " + errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @GetMapping("/{eventCode}")
    public ResponseEntity<Object> getEvent(@PathVariable String eventCode) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.eventHandler().getEventByCode(eventCode, new RequestCallback<Event>() {
            @Override
            public void onSuccess(Event responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(500).body("Error retrieving event: " + errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @GetMapping("/current")
    public ResponseEntity<Object> getCurrentEvent() {
        try {
            Event event = ScoringService.eventHandler().getCurrentEvent();
            return ResponseEntity.ok(Map.of("currentEvent", event));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Unable to retrieve current event."));
        }
    }

    @PostMapping("/create")
    public ResponseEntity<Object> createEvent(@RequestBody Event event) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.eventHandler().createEvent(event, new RequestCallback<Event>() {
            @Override
            public void onSuccess(Event responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(500).body("Error creating event: " + errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/update")
    public ResponseEntity<Object> updateEvent(@RequestBody Event event) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.eventHandler().updateEvent(event, new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(500).body("Error updating event: " + errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/delete")
    public ResponseEntity<Object> deleteEvent(@RequestBody Map<String, Object> body) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        String eventCode = body.get("eventCode").toString();
        boolean cleanDelete = body.get("cleanDelete") != null && (boolean) body.get("cleanDelete");
        ScoringService.eventHandler().deleteEventByCode(eventCode, cleanDelete, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void responseObject, String message) {
                future.complete(ResponseEntity.ok().body(Map.of("message", "Event deleted successfully.")));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                switch (errorCode) {
                    case ErrorCode.DELETE_FAILED:
                        future.complete(ResponseEntity.status(400).body(Map.of("error", "Failed to delete event: " + errorMessage)));
                        break;
                    default:
                        future.complete(ResponseEntity.status(500).body(Map.of("error", "Error deleting event: " + errorMessage)));
                }
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/set")
    public ResponseEntity<Object> setupEvent(@RequestBody Map<String, Object> body) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        String eventCode = body.get("eventCode").toString();
        ScoringService.eventHandler().setSystemEvent(eventCode, new RequestCallback<Event>() {
            @Override
            public void onSuccess(Event responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(500).body("Error setting up event: " + errorMessage));
            }
        });

        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/clear-current")
    public ResponseEntity<Object> clearCurrentEvent() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.eventHandler().clearCurrentEvent(new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void responseObject, String message) {
                future.complete(ResponseEntity.ok().body(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(500).body(Map.of("error", "Error clearing current event: " + errorMessage)));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

}
