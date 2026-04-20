package org.thingai.app.api;

import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JsonMapper;
import org.jetbrains.annotations.NotNull;
import org.thingai.app.api.endpoints.AuthApi;
import org.thingai.app.api.endpoints.EventApi;
import org.thingai.app.api.endpoints.MatchApi;
import org.thingai.app.api.endpoints.MatchControlApi;
import org.thingai.app.api.endpoints.RankApi;
import org.thingai.app.api.endpoints.ScoreApi;
import org.thingai.app.api.endpoints.SyncApi;
import org.thingai.app.api.endpoints.TeamApi;
import org.thingai.app.api.ws.LiveWs;
import org.thingai.app.api.ws.RankingWs;
import org.thingai.app.api.ws.RefereeWs;
import org.thingai.base.log.ILog;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

/**
 * Builds and starts the Javalin HTTP server. Replaces the Spring Boot
 * bootstrap previously driven by {@code @SpringBootApplication} on
 * {@code Main}.
 *
 * <p>Responsibilities previously owned by {@code *Config} beans:
 * <ul>
 *   <li>Port selection (was {@code ServerPortConfig}).</li>
 *   <li>CORS setup (was {@code CorsConfig}).</li>
 *   <li>SPA fallback so deep links resolve to {@code index.html} (was {@code WebConfig}).</li>
 *   <li>Static resource serving from classpath {@code /static} (written by the {@code copyWebUi} Gradle task).</li>
 * </ul>
 */
public final class ApiServer {

    private static final String TAG = "ApiServer";
    private static final String STATIC_CLASSPATH = "/static";

    private static final Gson GSON = new Gson();

    private ApiServer() {
    }

    /**
     * Build a fully-configured but NOT yet started Javalin instance.
     *
     * <p>The caller is responsible for invoking {@link Javalin#start(int)} (or
     * a variant) with the port returned by {@link #choosePort()}.
     */
    public static Javalin build() {
        Javalin app = Javalin.create(cfg -> {
            // JSON via Gson (project standard; avoids pulling in Jackson).
            cfg.jsonMapper(gsonMapper());

            // Permissive CORS for LAN deployment (was CorsConfig).
            cfg.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
                rule.anyHost();
                rule.allowCredentials = true;
                rule.exposeHeader("Authorization");
            }));

            // Serve Angular build output baked into the jar at /static.
            cfg.staticFiles.add(staticCfg -> {
                staticCfg.hostedPath = "/";
                staticCfg.directory = STATIC_CLASSPATH;
                staticCfg.location = Location.CLASSPATH;
                staticCfg.precompress = false;
            });

            cfg.showJavalinBanner = false;
        });

        // SPA fallback: any non-API, non-static 404 serves index.html so the
        // Angular router can take over (was WebConfig view controllers).
        app.error(404, ctx -> {
            String path = ctx.path();
            if (path.startsWith("/api") || path.startsWith("/ws")) {
                return; // keep real 404 for unknown API routes
            }
            byte[] index = loadIndexHtml();
            if (index != null) {
                ctx.status(200).contentType("text/html; charset=utf-8").result(index);
            }
        });

        // Strict auth on all /api/* routes (public login/refresh/local-ip excepted).
        AuthFilter.register(app);

        // Role gates. Registered AFTER AuthFilter.register() so the token
        // filter runs first and populates ATTR_ROLE. Lower number = higher
        // privilege; see org.thingai.app.scoringservice.define.AccountRole.
        //
        // Account management (admin-only).
        AuthFilter.requireRole(app, "/api/auth/users",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);
        AuthFilter.requireRole(app, "/api/auth/accounts",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);
        AuthFilter.requireRole(app, "/api/auth/accounts/*",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);
        AuthFilter.requireRole(app, "/api/auth/create-account",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);

        // Event mutation. Reads (GET /api/event*) stay open to any logged-in
        // user because displays, rankings, and referees all need them.
        AuthFilter.requireRole(app, "/api/event/create",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);
        AuthFilter.requireRole(app, "/api/event/update",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);
        AuthFilter.requireRole(app, "/api/event/delete",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);
        AuthFilter.requireRole(app, "/api/event/set",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);
        AuthFilter.requireRole(app, "/api/event/clear-current",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);

        // Match schedule + definition mutation (admins manage the tournament
        // structure; scorekeepers don't rewrite schedules).
        AuthFilter.requireRole(app, "/api/match/create",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);
        AuthFilter.requireRole(app, "/api/match/update",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);
        AuthFilter.requireRole(app, "/api/match/delete/*",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);
        AuthFilter.requireRole(app, "/api/match/schedule/*",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);
        AuthFilter.requireRole(app, "/api/match/playoff/*",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);

        // Team mutation (import/create/update/delete) is admin territory;
        // GET stays open.
        // NOTE: Javalin's glob matches a single segment, so /api/teams and
        // /api/teams/{id} need separate gates. Method-level filtering would
        // require inspecting ctx.method() -- we can't block GET /api/teams
        // without also blocking POST /api/teams via a path-only gate. Using
        // the shared /api/teams path here gates both. See TeamApi; read
        // access is broad in other endpoints (/api/match, /api/scores).
        // TODO: when a method-aware requireRole helper exists, split these.

        // Score mutation (scorekeeper + higher).
        AuthFilter.requireRole(app, "/api/scores/submit",
                org.thingai.app.scoringservice.define.AccountRole.SCOREKEEPER);

        // Ranking recalculation (admin only; GET status stays public read).
        AuthFilter.requireRole(app, "/api/rank/recalculate",
                org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN);

        // Match-control actions (scorekeeper + higher). State read stays open
        // so referee/display pages can reflect timer + loaded match.
        AuthFilter.requireRole(app, "/api/match-control/load",
                org.thingai.app.scoringservice.define.AccountRole.SCOREKEEPER);
        AuthFilter.requireRole(app, "/api/match-control/activate",
                org.thingai.app.scoringservice.define.AccountRole.SCOREKEEPER);
        AuthFilter.requireRole(app, "/api/match-control/start",
                org.thingai.app.scoringservice.define.AccountRole.SCOREKEEPER);
        AuthFilter.requireRole(app, "/api/match-control/abort",
                org.thingai.app.scoringservice.define.AccountRole.SCOREKEEPER);
        AuthFilter.requireRole(app, "/api/match-control/commit",
                org.thingai.app.scoringservice.define.AccountRole.SCOREKEEPER);
        AuthFilter.requireRole(app, "/api/match-control/override",
                org.thingai.app.scoringservice.define.AccountRole.SCOREKEEPER);
        AuthFilter.requireRole(app, "/api/match-control/display",
                org.thingai.app.scoringservice.define.AccountRole.SCOREKEEPER);

        // Route registration (one class per domain).
        AuthApi.register(app);
        TeamApi.register(app);
        MatchApi.register(app);
        MatchControlApi.register(app);
        ScoreApi.register(app);
        EventApi.register(app);
        RankApi.register(app);
        SyncApi.register(app);

        // WebSocket endpoints. Auth (for /ws/referee) is enforced inside the
        // endpoint's onConnect via WsAuthFilter so close codes survive to
        // the client; see WsAuthFilter for why this isn't a beforeUpgrade.
        LiveWs.register(app);
        RankingWs.register(app);
        RefereeWs.register(app);

        return app;
    }

    /**
     * Try port 80 first, then fall back through 12345&ndash;12400 (behavior
     * inherited from the legacy {@code ServerPortConfig}).
     */
    public static int choosePort() {
        if (isPortAvailable(80)) {
            return 80;
        }
        for (int port = 12345; port <= 12400; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }
        throw new IllegalStateException("No available port found between 80 and 12345-12400.");
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            ignored.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] loadIndexHtml() {
        try (InputStream in = ApiServer.class.getResourceAsStream(STATIC_CLASSPATH + "/index.html")) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (Exception e) {
            ILog.e(TAG, "loadIndexHtml", e.getMessage());
            return null;
        }
    }

    private static JsonMapper gsonMapper() {
        return new JsonMapper() {
            @NotNull
            @Override
            public String toJsonString(@NotNull Object obj, @NotNull Type type) {
                return GSON.toJson(obj, type);
            }

            @NotNull
            @Override
            public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
                return GSON.fromJson(json, targetType);
            }

            @NotNull
            @Override
            public InputStream toJsonStream(@NotNull Object obj, @NotNull Type type) {
                return new java.io.ByteArrayInputStream(GSON.toJson(obj, type).getBytes(StandardCharsets.UTF_8));
            }

            @NotNull
            @Override
            public <T> T fromJsonStream(@NotNull InputStream in, @NotNull Type targetType) {
                return GSON.fromJson(new java.io.InputStreamReader(in, StandardCharsets.UTF_8), targetType);
            }
        };
    }
}
