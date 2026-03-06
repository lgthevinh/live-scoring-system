package org.thingai.app.scoringservice.define.ui;

public enum ScoreValueType {
    INTEGER(1),
    BOOLEAN(2);

    private final int value;

    ScoreValueType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}