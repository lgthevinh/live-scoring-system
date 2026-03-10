package org.thingai.app.scoringservice.entity;

import org.thingai.base.dao.annotations.DaoColumn;
import org.thingai.base.dao.annotations.DaoTable;

@DaoTable(name = "auth_data")
public class AuthData {
    @DaoColumn(name = "username", primaryKey = true, unique = true)
    private String username;

    @DaoColumn(nullable = false)
    private String password;

    @DaoColumn(nullable = false)
    private String salt;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }
}
