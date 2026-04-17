package org.thingai.app.api.endpoints;

import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.thingai.app.api.utils.ResponseEntityUtil.PendingResponse;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.AllianceTeam;
import org.thingai.app.scoringservice.entity.Match;
import org.thingai.app.scoringservice.entity.TimeBlock;
import org.thingai.base.log.ILog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.api.utils.ResponseEntityUtil.badRequest;
import static org.thingai.app.api.utils.ResponseEntityUtil.errorResponse;
import static org.thingai.app.api.utils.ResponseEntityUtil.writeFuture;

/**
 * REST surface for matches and schedule generation
 * (base path {@code /api/match}).
 */
public final class MatchApi {

    private static final Gson GSON = new Gson();

    private MatchApi() {
    }

    public static void register(Javalin app) {
        app.post("/api/match/create", MatchApi::createMatch);
        app.get("/api/match", MatchApi::listMatches);
        app.get("/api/match/{id}", MatchApi::getMatch);
        app.get("/api/match/list/{matchType}", MatchApi::listMatchesByType);
        app.get("/api/match/list/details/{matchType}", MatchApi::listMatchDetails);
        app.put("/api/match/update", MatchApi::updateMatch);
        app.delete("/api/match/delete/{id}", MatchApi::deleteMatch);
        app.post("/api/match/schedule/generate", MatchApi::generateSchedule);
        app.post("/api/match/schedule/generate/v2", MatchApi::generateSchedule);
        app.post("/api/match/playoff/generate", MatchApi::generatePlayoffSchedule);
        app.post("/api/match/schedule/generate/playoff", MatchApi::generatePlayoffSchedule);
    }

    private static void createMatch(Context ctx) {
        @SuppressWarnings("unchecked")
        Map<String, Object> request = ctx.bodyAsClass(Map.class);

        Integer matchType = parseInt(request == null ? null : request.get("matchType"));
        Integer matchNumber = parseInt(request == null ? null : request.get("matchNumber"));
        String matchStartTime = request == null ? null : (String) request.get("matchStartTime");
        Integer fieldNumber = parseInt(request == null ? null : request.get("fieldNumber"));

        String[] redTeamIds = parseStringArray(request == null ? null : request.get("redTeamIds"));
        String[] blueTeamIds = parseStringArray(request == null ? null : request.get("blueTeamIds"));

        if (matchType == null || matchNumber == null || isBlank(matchStartTime)) {
            badRequest(ctx, "matchType, matchNumber, and matchStartTime are required.");
            return;
        }
        if (redTeamIds.length == 0 || blueTeamIds.length == 0) {
            badRequest(ctx, "redTeamIds and blueTeamIds are required.");
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.matchHandler().createMatch(
                matchType,
                matchNumber,
                fieldNumber == null ? 1 : fieldNumber,
                matchStartTime,
                redTeamIds,
                blueTeamIds,
                new RequestCallback<Match>() {
                    @Override
                    public void onSuccess(Match responseObject, String message) {
                        future.complete(PendingResponse.ok(Map.of("message", message, "matchId", responseObject.getId())));
                    }

                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        future.complete(errorResponse(errorCode, errorMessage));
                    }
                }
        );
        writeFuture(ctx, future);
    }

    private static void getMatch(Context ctx) {
        String id = ctx.pathParam("id");
        if (isBlank(id)) {
            badRequest(ctx, "Match id is required.");
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.matchHandler().getMatch(id, new RequestCallback<Match>() {
            @Override
            public void onSuccess(Match responseObject, String message) {
                future.complete(PendingResponse.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void listMatches(Context ctx) {
        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.matchHandler().listMatches(new RequestCallback<Match[]>() {
            @Override
            public void onSuccess(Match[] responseObject, String message) {
                future.complete(PendingResponse.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void listMatchesByType(Context ctx) {
        int matchType;
        try {
            matchType = Integer.parseInt(ctx.pathParam("matchType"));
        } catch (NumberFormatException e) {
            badRequest(ctx, "Invalid matchType.");
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.matchHandler().listMatchesByType(matchType, new RequestCallback<Match[]>() {
            @Override
            public void onSuccess(Match[] responseObject, String message) {
                future.complete(PendingResponse.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void listMatchDetails(Context ctx) {
        int matchType;
        try {
            matchType = Integer.parseInt(ctx.pathParam("matchType"));
        } catch (NumberFormatException e) {
            badRequest(ctx, "Invalid matchType.");
            return;
        }
        boolean withScore = Boolean.parseBoolean(ctx.queryParam("withScore"));

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.matchHandler().listMatchDetailsByType(matchType, withScore,
                new RequestCallback<MatchDetailDto[]>() {
                    @Override
                    public void onSuccess(MatchDetailDto[] responseObject, String message) {
                        future.complete(PendingResponse.ok(responseObject));
                    }

                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        future.complete(errorResponse(errorCode, errorMessage));
                    }
                });
        writeFuture(ctx, future);
    }

    private static void updateMatch(Context ctx) {
        Match match = ctx.bodyAsClass(Match.class);
        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.matchHandler().updateMatch(match, new RequestCallback<Match>() {
            @Override
            public void onSuccess(Match responseObject, String message) {
                future.complete(PendingResponse.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void deleteMatch(Context ctx) {
        String id = ctx.pathParam("id");
        if (isBlank(id)) {
            badRequest(ctx, "Match id is required.");
            return;
        }
        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.matchHandler().deleteMatch(id, new RequestCallback<Void>() {
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

    private static void generateSchedule(Context ctx) {
        @SuppressWarnings("unchecked")
        Map<String, Object> request = ctx.bodyAsClass(Map.class);

        Integer rounds = parseInt(request == null ? null : request.get("rounds"));
        Integer matchDuration = parseInt(request == null ? null : request.get("matchDuration"));
        Integer fieldCount = parseInt(request == null ? null : request.get("fieldCount"));
        String startTime = request == null ? null : (String) request.get("startTime");
        TimeBlock[] timeBlocks = parseTimeBlocks(request == null ? null : request.get("timeBlocks"));

        if (rounds == null || matchDuration == null || isBlank(startTime)) {
            badRequest(ctx, "rounds, matchDuration, and startTime are required.");
            return;
        }

        int resolvedFieldCount = fieldCount == null ? 1 : fieldCount;

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.scheduleHandler().generateSchedule(
                rounds,
                startTime,
                matchDuration,
                resolvedFieldCount,
                timeBlocks,
                new RequestCallback<Void>() {
                    @Override
                    public void onSuccess(Void responseObject, String message) {
                        ILog.d("MatchApi", "generateSchedule:onSuccess " + message);
                        future.complete(PendingResponse.ok(Map.of("message", message)));
                    }

                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        ILog.d("MatchApi", "generateSchedule:onFailure " + errorMessage);
                        future.complete(errorResponse(errorCode, errorMessage));
                    }
                }
        );
        writeFuture(ctx, future);
    }

    private static void generatePlayoffSchedule(Context ctx) {
        @SuppressWarnings("unchecked")
        Map<String, Object> request = ctx.bodyAsClass(Map.class);

        Integer playoffType = parseInt(request == null ? null : request.get("playoffType"));
        Integer fieldCount = parseInt(request == null ? null : request.get("fieldCount"));
        Integer matchDuration = parseInt(request == null ? null : request.get("matchDuration"));
        String startTime = request == null ? null : (String) request.get("startTime");

        AllianceTeam[] allianceTeams = parseAllianceTeams(request == null ? null : request.get("allianceTeams"));
        TimeBlock[] timeBlocks = parseTimeBlocks(request == null ? null : request.get("timeBlocks"));

        if (playoffType == null || matchDuration == null || fieldCount == null || isBlank(startTime)) {
            badRequest(ctx, "playoffType, fieldCount, matchDuration, and startTime are required.");
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.scheduleHandler().generatePlayoffSchedule(
                playoffType,
                fieldCount,
                allianceTeams,
                startTime,
                matchDuration,
                timeBlocks,
                new RequestCallback<Void>() {
                    @Override
                    public void onSuccess(Void responseObject, String message) {
                        future.complete(PendingResponse.ok(Map.of("message", message)));
                    }

                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        future.complete(errorResponse(errorCode, errorMessage));
                    }
                }
        );
        writeFuture(ctx, future);
    }

    // --- helpers -------------------------------------------------------------

    /**
     * Convert a loosely-typed value (Map/List parsed by Gson from an
     * {@code Object} field) into a strongly-typed array by re-serializing
     * through JSON. Mirrors what {@code ObjectMapper.convertValue} did.
     */
    private static TimeBlock[] parseTimeBlocks(Object value) {
        if (value == null) {
            return new TimeBlock[0];
        }
        return GSON.fromJson(GSON.toJson(value), TimeBlock[].class);
    }

    private static AllianceTeam[] parseAllianceTeams(Object value) {
        if (value == null) {
            return new AllianceTeam[0];
        }
        return GSON.fromJson(GSON.toJson(value), AllianceTeam[].class);
    }

    private static String[] parseStringArray(Object value) {
        if (value == null) {
            return new String[0];
        }
        if (value instanceof String strValue) {
            if (strValue.trim().isEmpty()) {
                return new String[0];
            }
            String[] parts = strValue.split(",");
            List<String> cleaned = new ArrayList<>();
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty()) {
                    cleaned.add(part.trim());
                }
            }
            return cleaned.toArray(new String[0]);
        }
        if (value instanceof List<?> list) {
            List<String> cleaned = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String text = item.toString().trim();
                if (!text.isEmpty()) {
                    cleaned.add(text);
                }
            }
            return cleaned.toArray(new String[0]);
        }
        return GSON.fromJson(GSON.toJson(value), String[].class);
    }

    private static Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue) {
            if (stringValue.trim().isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
