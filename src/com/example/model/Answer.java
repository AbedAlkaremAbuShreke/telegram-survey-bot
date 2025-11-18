package com.example.model;

import java.time.Instant;

/**
 * Answer model for storing a user's vote.
 */
public class Answer {
    public long id;
    public long pollId;
    public long questionId;
    public long choiceId;
    public long userId;
    public Instant answeredAt;
}
