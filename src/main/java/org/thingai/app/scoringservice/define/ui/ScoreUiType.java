package org.thingai.app.scoringservice.define.ui;

public enum ScoreUiType {
    COUNTER(1),
    TOGGLE(2);

    private final int value;

    ScoreUiType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}