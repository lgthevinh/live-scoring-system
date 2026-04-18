package org.thingai.app.api.ws;

/**
 * WebSocket close codes used by this application.
 *
 * <p>All custom codes are in the {@code 4xxx} range, which is reserved by RFC
 * 6455 for application-defined codes. Using values that mirror the HTTP
 * equivalents (400/401/403/404/500) keeps the meaning obvious in client logs.
 *
 * <p>Code {@code 1000} ({@link #NORMAL}) is the IANA-defined value for a
 * graceful close and is included here only so call sites don't need to mix
 * "magic 1000" with the named constants.
 */
public final class WsCloseCode {

    private WsCloseCode() {
    }

    public static final int NORMAL = 1000;

    public static final int BAD_REQUEST = 4400;
    public static final int UNAUTHORIZED = 4401;
    public static final int FORBIDDEN = 4403;
    public static final int NOT_FOUND = 4404;
    public static final int SERVER_ERROR = 4500;
}
