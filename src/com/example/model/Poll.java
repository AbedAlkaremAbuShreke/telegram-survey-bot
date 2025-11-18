package com.example.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Poll model containing questions and metadata.
 */
public class Poll {
    public long id;
    public String title;
    public long creatorId;
    public Instant createdAt;
    public Instant expiresAt; // nullable
    public String status; // ACTIVE or CLOSED
    public List<Question> questions = new ArrayList<>();

    public Poll() {}
}
