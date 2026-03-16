package org.thingai.app.scoringservice.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MatchTimerService {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> timerTask;

    private final int startSeconds;
    private int remainingSeconds;
    private boolean isRunning;

    private TimerCallback callback;

    public MatchTimerService(int startSeconds) {
        this.startSeconds = startSeconds;

    }

    public void startTimer(int totalSeconds) {
        stopTimer();
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
        callback.onTimerUpdated(remainingSeconds);
    }

    private void tick() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
            callback.onTimerUpdated(remainingSeconds);
        } else {
            stopTimer();
            callback.onTimerEnded();
        }
    }

    public void resetTimer() {
        stopTimer();
        remainingSeconds = startSeconds;
        callback.onTimerUpdated(remainingSeconds);
    }

    public void setCallback(TimerCallback callback) {
        this.callback = callback;
    }

    public interface TimerCallback {
        void onTimerEnded();
        void onTimerUpdated(int remainingSeconds);
    }
}

