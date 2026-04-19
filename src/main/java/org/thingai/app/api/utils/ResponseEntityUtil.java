package org.thingai.app.api.utils;

import com.google.gson.Gson;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.thingai.app.scoringservice.define.ErrorCode;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Javalin response helpers. Replaces the Spring {@code ResponseEntity} based
 * helper with direct writes to a Javalin {@link Context}.
 */
public class ResponseEntityUtil {

    /** Shared Gson instance used where a direct (non-Javalin) serializer is needed. */
    public static final Gson GSON = new Gson();

    private ResponseEntityUtil() {
    }

    /**
     * Block on an async result and write it to the given context.
     * The callback-producing code in handlers completes the future with a
     * {@link PendingResponse}; this method applies that to the Javalin context.
     */
    public static void writeFuture(Context ctx, CompletableFuture<PendingResponse> future) {
        try {
            PendingResponse pending = future.get();
            pending.applyTo(ctx);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .json(Map.of("error", "Request interrupted."));
        } catch (ExecutionException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .json(Map.of("error", "An unexpected error occurred."));
        }
    }

    /**
     * Build an error {@link PendingResponse} from a domain {@link ErrorCode}.
     * Mirrors the status mapping of the old Spring helper.
     */
    public static PendingResponse errorResponse(int errorCode, String errorMessage) {
        HttpStatus status = switch (errorCode) {
            case ErrorCode.DAO_CREATE_FAILED,
                 ErrorCode.DAO_UPDATE_FAILED,
                 ErrorCode.DAO_DELETE_FAILED -> HttpStatus.BAD_REQUEST;
            case ErrorCode.DAO_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return new PendingResponse(status, Map.of("errorCode", errorCode, "error", errorMessage));
    }

    /** Convenience: write an error directly to a context. */
    public static void writeError(Context ctx, int errorCode, String errorMessage) {
        errorResponse(errorCode, errorMessage).applyTo(ctx);
    }

    /** Convenience: write a 400 with a simple message. */
    public static void badRequest(Context ctx, String message) {
        ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", message));
    }

    /** Convenience: write a 404 with a simple message. */
    public static void notFound(Context ctx, String message) {
        ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", message));
    }

    /** Convenience: write a 409 with a simple message. */
    public static void conflict(Context ctx, String message) {
        ctx.status(HttpStatus.CONFLICT).json(Map.of("error", message));
    }

    /** Convenience: write a 500 with a simple message. */
    public static void internalError(Context ctx, String message) {
        ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", message));
    }

    /** Convenience: write 200 OK with a body. */
    public static void ok(Context ctx, Object body) {
        ctx.status(HttpStatus.OK).json(body);
    }

    /**
     * Value object used by async handlers to hand a completed result back to
     * the request thread without dragging Spring's ResponseEntity around.
     */
    public static final class PendingResponse {
        private final HttpStatus status;
        private final Object body;

        public PendingResponse(HttpStatus status, Object body) {
            this.status = status;
            this.body = body;
        }

        public static PendingResponse ok(Object body) {
            return new PendingResponse(HttpStatus.OK, body);
        }

        public static PendingResponse status(HttpStatus status, Object body) {
            return new PendingResponse(status, body);
        }

        public void applyTo(Context ctx) {
            ctx.status(status);
            if (body != null) {
                ctx.json(body);
            }
        }
    }
}
