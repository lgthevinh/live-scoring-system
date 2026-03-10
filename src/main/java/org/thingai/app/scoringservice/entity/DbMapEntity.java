package org.thingai.app.scoringservice.entity;

import org.thingai.base.dao.annotations.DaoColumn;
import org.thingai.base.dao.annotations.DaoTable;

@DaoTable(name = "db_map")
public class DbMapEntity {
    @DaoColumn(primaryKey = true, unique = true)
    private String key;

    @DaoColumn(nullable = false)
    private String value;

    public DbMapEntity() {
    }

    public DbMapEntity(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
