package org.thingai.app.api.endpoints;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.thingai.app.api.utils.ResponseEntityUtil.PendingResponse;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.entity.Event;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.api.utils.ResponseEntityUtil.badRequest;
import static org.thingai.app.api.utils.ResponseEntityUtil.errorResponse;
import static org.thingai.app.api.utils.ResponseEntityUtil.writeFuture;

/**
 * REST surface for events (base path {@code /api/event}).
 */
public final class EventApi {

    private EventApi() {
    }

    public static void register(Javalin app) {
        app.get("/api/event", EventApi::listEvents);
        app.get("/api/event/", EventApi::listEvents);
        app.get("/api/event/current", EventApi::getCurrentEvent);
        app.get("/api/event/{eventCode}", EventApi::getEvent);
        app.post("/api/event/create", EventApi::createEvent);
        app.post("/api/event/update", EventApi::updateEvent);
        app.post("/api/event/delete", EventApi::deleteEvent);
        app.post("/api/event/set", EventApi::setSystemEvent);
        app.post("/api/event/clear-current", EventApi::clearCurrentEvent);
    }

    private static void listEvents(Context ctx) {
        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.eventHandler().listEvents(new RequestCallback<Event[]>() {
            @Override
            public void onSuccess(Event[] responseObject, String message) {
                future.complete(PendingResponse.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void getCurrentEvent(Context ctx) {
        Event currentEvent = ScoringService.eventHandler().getCurrentEvent();
        if (currentEvent == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("message", "No current event set."));
            return;
        }
        ctx.json(Map.of("currentEvent", currentEvent));
    }

    private static void getEvent(Context ctx) {
        String eventCode = ctx.pathParam("eventCode");
        if (isBlank(eventCode)) {
            badRequest(ctx, "eventCode is required.");
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.eventHandler().getEventByCode(eventCode, new RequestCallback<Event>() {
            @Override
            public void onSuccess(Event responseObject, String message) {
                future.complete(PendingResponse.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void createEvent(Context ctx) {
        Event event = ctx.bodyAsClass(Event.class);
        if (event == null || isBlank(event.getEventCode()) || isBlank(event.getName())) {
            badRequest(ctx, "eventCode and name are required.");
            return;
        }
        if (isBlank(event.getUuid())) {
            event.setUuid(event.getEventCode());
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.eventHandler().createEvent(event, new RequestCallback<Event>() {
            @Override
            public void onSuccess(Event responseObject, String message) {
                future.complete(PendingResponse.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void updateEvent(Context ctx) {
        Event event = ctx.bodyAsClass(Event.class);
        if (event == null || isBlank(event.getEventCode())) {
            badRequest(ctx, "eventCode is required.");
            return;
        }
        if (isBlank(event.getUuid())) {
            event.setUuid(event.getEventCode());
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.eventHandler().updateEvent(event, new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                future.complete(PendingResponse.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void deleteEvent(Context ctx) {
        @SuppressWarnings("unchecked")
        Map<String, Object> request = ctx.bodyAsClass(Map.class);
        String eventCode = request == null ? null : (String) request.get("eventCode");
        boolean cleanDelete = parseBoolean(request == null ? null : request.get("cleanDelete"), false);

        if (isBlank(eventCode)) {
            badRequest(ctx, "eventCode is required.");
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.eventHandler().deleteEventByCode(eventCode, cleanDelete, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void responseObject, String message) {
                future.complete(PendingResponse.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void setSystemEvent(Context ctx) {
        @SuppressWarnings("unchecked")
        Map<String, String> request = ctx.bodyAsClass(Map.class);
        String eventCode = request == null ? null : request.get("eventCode");
        if (isBlank(eventCode)) {
            badRequest(ctx, "eventCode is required.");
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.eventHandler().setSystemEvent(eventCode, new RequestCallback<Event>() {
            @Override
            public void onSuccess(Event responseObject, String message) {
                future.complete(PendingResponse.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void clearCurrentEvent(Context ctx) {
        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.eventHandler().clearCurrentEvent(new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void responseObject, String message) {
                future.complete(PendingResponse.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean parseBoolean(Object value, boolean defaultValue) {
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
