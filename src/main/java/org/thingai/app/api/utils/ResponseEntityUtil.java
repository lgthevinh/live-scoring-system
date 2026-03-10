package org.thingai.app.api.utils;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.thingai.app.scoringservice.define.ErrorCode;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ResponseEntityUtil {

    public static synchronized ResponseEntity<Object> getObjectResponse(CompletableFuture<ResponseEntity<Object>> future) {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    public static synchronized ResponseEntity<Object> createErrorResponse(int errorCode, String errorMessage) {
        Map<String, Object> body = Map.of("errorCode", errorCode, "error", errorMessage);
        HttpStatus status = switch (errorCode) {
            case ErrorCode.DAO_CREATE_FAILED,
                 ErrorCode.DAO_UPDATE_FAILED,
                 ErrorCode.DAO_DELETE_FAILED -> HttpStatus.BAD_REQUEST;
            case ErrorCode.DAO_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(body);
    }
}
