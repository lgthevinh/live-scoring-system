package org.thingai.app.scoringservice.entity;

import org.thingai.base.dao.annotations.DaoColumn;
import org.thingai.base.dao.annotations.DaoTable;

@DaoTable(name = "event")
public class Event {
    @DaoColumn(name = "uuid", primaryKey = true)
    private String uuid;

    @DaoColumn(name = "name")
    private String name;

    @DaoColumn(name = "eventCode", unique = true)
    private String eventCode;

    @DaoColumn(name = "fieldCount")
    private int fieldCount = 1;

    @DaoColumn(name = "date")
    private String date;

    @DaoColumn(name = "location")
    private String location;

    @DaoColumn(name = "description")
    private String description;

    @DaoColumn(name = "website")
    private String website;

    @DaoColumn(name = "organizer")
    private String organizer;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getFieldCount() {
        return fieldCount;
    }

    public void setFieldCount(int fieldCount) {
        this.fieldCount = fieldCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }
}
