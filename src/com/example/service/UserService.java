package com.example.service;

import com.example.model.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User service: register users and list community members.
 * Now uses in-memory storage (ConcurrentHashMap) instead of a database.
 */
public class UserService {
    // In-memory storage: Map<Telegram ID, User Object>
    final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong nextUserId = new AtomicLong(1);

    public UserService() {
        // Constructor no longer needs DatabaseManager
    }

    /**
     * Registers a user if they don't exist, or returns the existing user.
     * @param tgId Telegram user ID
     * @param displayName User's display name
     * @return The registered or existing User object
     */
    public User registerIfNotExists(long tgId, String displayName) {
        // Check if user already exists by Telegram ID
        if (users.containsKey(tgId)) {
            return users.get(tgId);
        }

        // Create new user
        User u = new User();
        u.id = nextUserId.getAndIncrement(); // Assign unique internal ID
        u.telegramId = tgId;
        u.displayName = displayName;
        u.joinedAt = Instant.now();

        // Store in memory
        users.put(tgId, u);
        System.out.println("New user registered: " + displayName + " (ID: " + u.id + ")");
        return u;
    }

    /**
     * Lists all registered users.
     * @return A list of all User objects
     */
    public List<User> listAll() {
        return new ArrayList<>(users.values());
    }

    /**
     * Counts the total number of registered users.
     * @return The count of users
     */
    public int countAll() {
        return users.size();
    }
}
