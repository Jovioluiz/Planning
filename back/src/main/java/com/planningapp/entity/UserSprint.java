package com.planningapp.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_sprints", uniqueConstraints = @UniqueConstraint(columnNames = {"username", "sprint"}))
public class UserSprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String sprint;

    public UserSprint() {}

    public UserSprint(String username, String sprint) {
        this.username = username;
        this.sprint = sprint;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getSprint() { return sprint; }
    public void setSprint(String sprint) { this.sprint = sprint; }
}
