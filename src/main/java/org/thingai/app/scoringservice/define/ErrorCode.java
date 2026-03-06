package org.thingai.app.scoringservice.define;

public class ErrorCode {
    public static final int GENERAL = 0;
    // DAO error codes
    public static final int DAO_CREATE_FAILED = 1;
    public static final int DAO_UPDATE_FAILED = 2;
    public static final int DAO_DELETE_FAILED = 3;
    public static final int DAO_RETRIEVE_FAILED = 4;

    public static final int CUSTOM_ERR = 255;
}
