package com.example.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Question model with multiple choices.
 */
public class Question {
    public long id;
    public long pollId;
    public String text;
    public int order;
    public List<Choice> choices = new ArrayList<>();
}
