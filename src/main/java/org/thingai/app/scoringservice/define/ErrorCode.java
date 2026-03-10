package org.thingai.app.scoringservice.define;

public class ErrorCode {
    public static final int GENERAL = 0;
    // DAO error codes
    public static final int DAO_CREATE_FAILED = 1;
    public static final int DAO_UPDATE_FAILED = 2;
    public static final int DAO_DELETE_FAILED = 3;
    public static final int DAO_RETRIEVE_FAILED = 4;
    public static final int DAO_NOT_FOUND = 5;

    // Authen error codes
    public static final int AUTHEN_INVALID_CREDENTIALS = 10;
    public static final int AUTHEN_TOKEN_EXPIRED = 11;
    public static final int AUTHEN_UNAUTHORIZED = 12;
    public static final int AUTHEN_USER_ALREADY_EXISTS = 13;
    public static final int AUTHEN_INVALID_TOKEN = 14;

    // Score calculation error codes
    public static final int SCORE_CALCULATION_FAILED = 20;
    public static final int SCORE_UPDATE_FAILED = 21;

    public static final int CUSTOM_ERR = 255;
}
