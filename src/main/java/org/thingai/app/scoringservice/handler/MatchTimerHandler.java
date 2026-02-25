package org.thingai.app.scoringservice.handler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MatchTimerHandler {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> timerTask;

    private final int startSeconds;
    private int remainingSeconds;
    private String matchId;
    private int fieldNumber;
    private boolean isRunning;

    private TimerCallback callback;

    public MatchTimerHandler(int startSeconds) {
        this.startSeconds = startSeconds;

    }

    public void startTimer(String matchId, int fieldNumber, int totalSeconds) {
        stopTimer();
        this.matchId = matchId;
        this.fieldNumber = fieldNumber;
        this.remainingSeconds = totalSeconds;
        this.timerTask = scheduler.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    public void pauseTimer() {
        isRunning = false;
        if (timerTask != null) timerTask.cancel(false);
    }

    public void resumeTimer() {
        if (!isRunning) {
            isRunning = true;
            timerTask = scheduler.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
        }
    }

    public void stopTimer() {
        isRunning = false;
        if (timerTask != null) timerTask.cancel(true);
        callback.onTimerUpdated(matchId, String.valueOf(fieldNumber), remainingSeconds);
    }

    private void tick() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
            callback.onTimerUpdated(matchId, String.valueOf(fieldNumber), remainingSeconds);
        } else {
            stopTimer();
            callback.onTimerEnded(matchId);
        }
    }

    public void resetTimer() {
        stopTimer();
        remainingSeconds = startSeconds;
        callback.onTimerUpdated(matchId, String.valueOf(fieldNumber), remainingSeconds);
    }

    public void setCallback(TimerCallback callback) {
        this.callback = callback;
    }

    public interface TimerCallback {
        void onTimerEnded(String matchId);
        void onTimerUpdated(String matchId, String fieldNumber, int remainingSeconds);
    }
}

