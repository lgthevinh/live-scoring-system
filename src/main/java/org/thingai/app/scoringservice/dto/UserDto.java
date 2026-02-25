package org.thingai.app.scoringservice.dto;

public class UserDto {
    private String username;
    private int role;

    public UserDto() {
    }

    public UserDto(String username, int role) {
        this.username = username;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getRole() {
        return role;
    }

    public void setRole(int role) {
        this.role = role;
    }
}
