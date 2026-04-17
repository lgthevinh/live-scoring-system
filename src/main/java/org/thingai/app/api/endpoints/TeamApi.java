package org.thingai.app.api.endpoints;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.thingai.app.api.utils.ResponseEntityUtil.PendingResponse;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.entity.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.api.utils.ResponseEntityUtil.badRequest;
import static org.thingai.app.api.utils.ResponseEntityUtil.errorResponse;
import static org.thingai.app.api.utils.ResponseEntityUtil.writeFuture;

/**
 * REST surface for teams (base path {@code /api/teams}).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST   /api/teams}          - create one team from a JSON body</li>
 *   <li>{@code GET    /api/teams}          - list all teams</li>
 *   <li>{@code GET    /api/teams/{id}}     - fetch a team by id</li>
 *   <li>{@code PUT    /api/teams/{id}}     - update a team (path id must match body)</li>
 *   <li>{@code DELETE /api/teams/{id}}     - delete a team</li>
 *   <li>{@code POST   /api/teams/import}   - bulk import: JSON array / JSON object / CSV</li>
 * </ul>
 */
public final class TeamApi {

    private static final Gson GSON = new Gson();

    private TeamApi() {
    }

    public static void register(Javalin app) {
        app.post("/api/teams", TeamApi::createTeam);
        app.get("/api/teams", TeamApi::listTeams);
        app.get("/api/teams/{id}", TeamApi::getTeam);
        app.put("/api/teams/{id}", TeamApi::updateTeam);
        app.delete("/api/teams/{id}", TeamApi::deleteTeam);
        app.post("/api/teams/import", TeamApi::importTeams);
    }

    private static void createTeam(Context ctx) {
        Team team = ctx.bodyAsClass(Team.class);
        if (team == null || hasMissingFields(team)) {
            badRequest(ctx, "teamId, teamName, teamSchool, teamRegion are required.");
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.teamHandler().createTeam(
                team.getTeamId(),
                team.getTeamName(),
                team.getTeamSchool(),
                team.getTeamRegion(),
                new RequestCallback<Team>() {
                    @Override
                    public void onSuccess(Team responseObject, String message) {
                        future.complete(PendingResponse.ok(responseObject));
                    }

                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        future.complete(errorResponse(errorCode, errorMessage));
                    }
                });
        writeFuture(ctx, future);
    }

    private static void listTeams(Context ctx) {
        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.teamHandler().listTeams(new RequestCallback<Team[]>() {
            @Override
            public void onSuccess(Team[] responseObject, String message) {
                future.complete(PendingResponse.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void getTeam(Context ctx) {
        String id = ctx.pathParam("id");
        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.teamHandler().getTeam(id, new RequestCallback<Team>() {
            @Override
            public void onSuccess(Team responseObject, String message) {
                future.complete(PendingResponse.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void updateTeam(Context ctx) {
        String teamId = ctx.pathParam("id");
        Team team = ctx.bodyAsClass(Team.class);

        if (team == null || isBlank(teamId)) {
            badRequest(ctx, "teamId is required.");
            return;
        }
        if (team.getTeamId() != null && !teamId.equals(team.getTeamId())) {
            badRequest(ctx, "teamId in path and body must match.");
            return;
        }
        team.setTeamId(teamId);
        if (hasMissingFields(team)) {
            badRequest(ctx, "teamId, teamName, teamSchool, teamRegion are required.");
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.teamHandler().updateTeam(team, new RequestCallback<Team>() {
            @Override
            public void onSuccess(Team responseObject, String message) {
                future.complete(PendingResponse.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    private static void deleteTeam(Context ctx) {
        String id = ctx.pathParam("id");
        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.teamHandler().deleteTeam(id, new RequestCallback<Void>() {
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

    private static void importTeams(Context ctx) {
        String body = ctx.body();
        Team[] teams;
        try {
            teams = parseTeamsFromBody(body);
        } catch (IllegalArgumentException e) {
            badRequest(ctx, e.getMessage());
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.teamHandler().importTeams(teams, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void responseObject, String message) {
                future.complete(PendingResponse.ok(Map.of("message", message, "count", teams.length)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(errorResponse(errorCode, errorMessage));
            }
        });
        writeFuture(ctx, future);
    }

    // --- payload parsing -----------------------------------------------------

    /**
     * Bulk-import body parser. Accepts three shapes:
     * <ul>
     *   <li>JSON array of Team objects (starts with {@code [})</li>
     *   <li>JSON object for a single Team (starts with <code>{</code>)</li>
     *   <li>CSV with columns teamId, teamName, teamSchool, teamRegion
     *       (optional header row tolerated)</li>
     * </ul>
     */
    private static Team[] parseTeamsFromBody(String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Request body is empty.");
        }

        if (trimmed.startsWith("[")) {
            try {
                Team[] teams = GSON.fromJson(trimmed, Team[].class);
                if (teams == null || teams.length == 0) {
                    throw new IllegalArgumentException("No teams provided.");
                }
                return teams;
            } catch (JsonSyntaxException e) {
                throw new IllegalArgumentException("Invalid JSON array payload.");
            }
        }

        if (trimmed.startsWith("{")) {
            try {
                Team team = GSON.fromJson(trimmed, Team.class);
                if (team == null) {
                    throw new IllegalArgumentException("Invalid team payload.");
                }
                return new Team[]{team};
            } catch (JsonSyntaxException e) {
                throw new IllegalArgumentException("Invalid JSON team payload.");
            }
        }

        return parseTeamsFromCsv(trimmed);
    }

    private static Team[] parseTeamsFromCsv(String csvBody) {
        String[] lines = csvBody.split("\\r?\\n");
        List<Team> teams = new ArrayList<>();

        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            List<String> fields = parseCsvLine(trimmed);
            if (fields.size() < 4) {
                throw new IllegalArgumentException("CSV row must have 4 columns: teamId, teamName, teamSchool, teamRegion.");
            }

            if (isHeaderRow(fields)) {
                continue;
            }

            String teamId = fields.get(0).trim();
            String teamName = fields.get(1).trim();
            String teamSchool = fields.get(2).trim();
            String teamRegion = fields.get(3).trim();

            if (isBlank(teamId) || isBlank(teamName) || isBlank(teamSchool) || isBlank(teamRegion)) {
                throw new IllegalArgumentException("CSV row contains empty team fields.");
            }

            teams.add(new Team(teamId, teamName, teamSchool, teamRegion));
        }

        if (teams.isEmpty()) {
            throw new IllegalArgumentException("No valid team rows found in CSV.");
        }

        return teams.toArray(new Team[0]);
    }

    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
                continue;
            }

            if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(c);
        }

        fields.add(current.toString());
        return fields;
    }

    private static boolean isHeaderRow(List<String> fields) {
        if (fields.size() < 2) {
            return false;
        }
        String first = fields.get(0).trim().toLowerCase();
        String second = fields.get(1).trim().toLowerCase();
        return (first.equals("teamid") || first.equals("id"))
                && (second.equals("teamname") || second.equals("name"));
    }

    private static boolean hasMissingFields(Team team) {
        return isBlank(team.getTeamId())
                || isBlank(team.getTeamName())
                || isBlank(team.getTeamSchool())
                || isBlank(team.getTeamRegion());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
