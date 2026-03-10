package org.thingai.app.scoringservice.entity;

import org.thingai.base.dao.annotations.DaoColumn;
import org.thingai.base.dao.annotations.DaoTable;

@DaoTable(name = "match")
public class Match {
    // Match ID format: matchCode + matchNumber (e.g., Q1, P2)
    @DaoColumn(name = "id", primaryKey = true)
    private String id;

    @DaoColumn(name = "matchCode", unique = true)
    private String matchCode;

    @DaoColumn(name = "matchType")
    private int matchType;

    @DaoColumn(name = "matchNumber")
    private int matchNumber;

    @DaoColumn(name = "fieldNumber", defaultValue = "1")
    private int fieldNumber;

    @DaoColumn(name = "matchStartTime")
    private String matchStartTime;

    @DaoColumn(name = "actualStartTime")
    private String actualStartTime;

    @DaoColumn(name = "matchEndTime")
    private String matchEndTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getMatchType() {
        return matchType;
    }

    public void setMatchType(int matchType) {
        this.matchType = matchType;
    }

    public int getMatchNumber() {
        return matchNumber;
    }

    public void setMatchNumber(int matchNumber) {
        this.matchNumber = matchNumber;
    }

    public String getMatchStartTime() {
        return matchStartTime;
    }

    public void setMatchStartTime(String matchStartTime) {
        this.matchStartTime = matchStartTime;
    }

    public String getMatchEndTime() {
        return matchEndTime;
    }

    public void setMatchEndTime(String matchEndTime) {
        this.matchEndTime = matchEndTime;
    }

    public String getMatchCode() {
        return matchCode;
    }

    public void setMatchCode(String matchCode) {
        this.matchCode = matchCode;
    }

    public String getActualStartTime() {
        return actualStartTime;
    }

    public void setActualStartTime(String actualStartTime) {
        this.actualStartTime = actualStartTime;
    }

    public int getFieldNumber() {
        return fieldNumber;
    }

    public void setFieldNumber(int fieldNumber) {
        this.fieldNumber = fieldNumber;
    }
}
