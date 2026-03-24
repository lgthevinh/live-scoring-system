package org.thingai.app.api.v2;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.entity.Event;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.api.utils.ResponseEntityUtil.createErrorResponse;
import static org.thingai.app.api.utils.ResponseEntityUtil.getObjectResponse;

@RestController
@RequestMapping("/api/event")
public class EventApi {

    @GetMapping({"", "/"})
    public ResponseEntity<Object> listEvents() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.eventHandler().listEvents(new RequestCallback<Event[]>() {
            @Override
            public void onSuccess(Event[] responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    @GetMapping("/current")
    public ResponseEntity<Object> getCurrentEvent() {
        Event currentEvent = ScoringService.eventHandler().getCurrentEvent();
        if (currentEvent == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No current event set."));
        }
        return ResponseEntity.ok(Map.of("currentEvent", currentEvent));
    }

    @GetMapping("/{eventCode}")
    public ResponseEntity<Object> getEvent(@PathVariable String eventCode) {
        if (isBlank(eventCode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventCode is required."));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.eventHandler().getEventByCode(eventCode, new RequestCallback<Event>() {
            @Override
            public void onSuccess(Event responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    @PostMapping("/create")
    public ResponseEntity<Object> createEvent(@RequestBody Event event) {
        if (event == null || isBlank(event.getEventCode()) || isBlank(event.getName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventCode and name are required."));
        }

        if (isBlank(event.getUuid())) {
            event.setUuid(event.getEventCode());
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.eventHandler().createEvent(event, new RequestCallback<Event>() {
            @Override
            public void onSuccess(Event responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    @PostMapping("/update")
    public ResponseEntity<Object> updateEvent(@RequestBody Event event) {
        if (event == null || isBlank(event.getEventCode())) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventCode is required."));
        }

        if (isBlank(event.getUuid())) {
            event.setUuid(event.getEventCode());
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.eventHandler().updateEvent(event, new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    @PostMapping("/delete")
    public ResponseEntity<Object> deleteEvent(@RequestBody Map<String, Object> request) {
        String eventCode = request == null ? null : (String) request.get("eventCode");
        boolean cleanDelete = parseBoolean(request == null ? null : request.get("cleanDelete"), false);

        if (isBlank(eventCode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventCode is required."));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.eventHandler().deleteEventByCode(eventCode, cleanDelete, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void responseObject, String message) {
                future.complete(ResponseEntity.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    @PostMapping("/set")
    public ResponseEntity<Object> setSystemEvent(@RequestBody Map<String, String> request) {
        String eventCode = request == null ? null : request.get("eventCode");

        if (isBlank(eventCode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventCode is required."));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.eventHandler().setSystemEvent(eventCode, new RequestCallback<Event>() {
            @Override
            public void onSuccess(Event responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    @PostMapping("/clear-current")
    public ResponseEntity<Object> clearCurrentEvent() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.eventHandler().clearCurrentEvent(new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void responseObject, String message) {
                future.complete(ResponseEntity.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return defaultValue;
    }
}
