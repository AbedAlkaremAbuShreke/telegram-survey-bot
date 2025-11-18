package com.example.model;

import java.time.Instant;

/**
 * Simple User model.
 */
public class User {
    public long id;
    public long telegramId;
    public String displayName;
    public Instant joinedAt;

    public User() {}
    public User(long telegramId, String displayName) {
        this.telegramId = telegramId;
        this.displayName = displayName;
        this.joinedAt = Instant.now();
    }
}
