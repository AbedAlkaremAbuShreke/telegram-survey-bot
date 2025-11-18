package com.example.service;

import com.example.model.Poll;

/**
 * Interface for listening to poll closing events.
 */
public interface PollCloseListener {
    void onPollClosed(Poll closedPoll);
}
