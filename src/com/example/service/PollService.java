package com.example.service;

import com.example.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * PollService:
 * - create poll (manual)
 * - enforce only one active poll
 * - record votes (ensuring one vote per user per question)
 * - compute results (counts and percentages)
 * - auto-close polls after expiration
 *
 * Now uses in-memory storage (ConcurrentHashMap) instead of a database.
 */
public class PollService {
    private PollCloseListener listener;
    private final UserService userService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // In-memory storage
    private final Map<Long, Poll> polls = new ConcurrentHashMap<>();
    // Key: Poll ID, Value: List of Answers for that poll
    private final Map<Long, List<Answer>> answers = new ConcurrentHashMap<>();
    private final AtomicLong nextPollId = new AtomicLong(1);
    private final AtomicLong nextQuestionId = new AtomicLong(1);
    private final AtomicLong nextChoiceId = new AtomicLong(1);
    private final AtomicLong nextAnswerId = new AtomicLong(1);

    public PollService(UserService userService) {
        this.userService = userService;
    }

    public void setPollCloseListener(PollCloseListener listener) {
        this.listener = listener;
    }

    /**
     * Finds the currently active poll. Since only one can be active, we return the first one found.
     */
    public synchronized Poll getActivePoll() {
        return polls.values().stream()
                .filter(p -> "ACTIVE".equals(p.status))
                .findFirst()
                .orElse(null);
    }

    public synchronized boolean hasActivePoll() {
        return getActivePoll() != null;
    }

    public synchronized Poll createPoll(String title, long creatorId, List<Question> questions, Integer delayMinutes) throws Exception {
        // Validation: at least 3 members
        if (userService.countAll() < 3) {
            throw new Exception("Need at least 3 members to create poll.");
        }

        // Validation: only one active poll
        if (hasActivePoll()) {
            throw new Exception("Active poll exists.");
        }

        // Validation: 1-3 questions
        if (questions == null || questions.isEmpty() || questions.size() > 3) {
            throw new Exception("Poll must have 1-3 questions.");
        }

        // Validation: each question must have 2-4 choices
        for (Question q : questions) {
            if (q.choices == null || q.choices.size() < 2 || q.choices.size() > 4) {
                throw new Exception("Each question must have 2-4 choices.");
            }
        }

        Instant now = Instant.now();
        Instant expires = delayMinutes == null ? now.plusSeconds(5*60) : now.plusSeconds(delayMinutes * 60L);

        // 1. Create Poll object and assign ID
        Poll poll = new Poll();
        poll.id = nextPollId.getAndIncrement();
        poll.title = title;
        poll.creatorId = creatorId;
        poll.createdAt = now;
        poll.expiresAt = expires;
        poll.status = "ACTIVE";
        poll.questions = new ArrayList<>();

        // 2. Assign IDs to Questions and Choices
        int qOrder = 1;
        for (Question q : questions) {
            q.id = nextQuestionId.getAndIncrement();
            q.pollId = poll.id;
            q.order = qOrder++;
            q.choices = q.choices.stream().map(ch -> {
                ch.id = nextChoiceId.getAndIncrement();
                ch.questionId = q.id;
                return ch;
            }).collect(Collectors.toList());
            poll.questions.add(q);
        }

        // 3. Store in memory
        polls.put(poll.id, poll);
        answers.put(poll.id, Collections.synchronizedList(new ArrayList<>())); // Initialize answers list

        // 4. Schedule auto-close
        long delaySeconds = delayMinutes == null ? 5*60 : delayMinutes * 60L;
        scheduler.schedule(() -> {
            closePollIfActive(poll.id);
        }, delaySeconds, TimeUnit.SECONDS);

        return poll;
    }

    /**
     * Record a vote (only first vote per user per question is accepted).
     * callbackData expected format: "vote:pollId:questionId:choiceId"
     */
    public void recordVote(String callbackData, long telegramUserId) throws Exception {
        String[] parts = callbackData.split(":");
        if (parts.length != 4 || !parts[0].equals("vote")) throw new IllegalArgumentException("Invalid callback data");
        long pollId = Long.parseLong(parts[1]);
        long questionId = Long.parseLong(parts[2]);
        long choiceId = Long.parseLong(parts[3]);

        Poll poll = polls.get(pollId);
        if (poll == null) throw new Exception("Poll not found");
        if (!"ACTIVE".equals(poll.status)) throw new Exception("Poll is not active");

        // Find internal user ID (Note: UserService now stores users by Telegram ID)
        User user = userService.users.get(telegramUserId);
        if (user == null) throw new Exception("User not registered");
        long userId = user.id;

        List<Answer> pollAnswers = answers.get(pollId);
        if (pollAnswers == null) throw new Exception("Answers list not initialized for poll");

        synchronized (pollAnswers) {
            // Check if user already answered THIS question in this poll
            boolean alreadyVoted = pollAnswers.stream()
                    .anyMatch(a -> a.userId == userId && a.questionId == questionId);

            if (alreadyVoted) throw new Exception("Already voted this question");

            // Insert answer
            Answer newAnswer = new Answer();
            newAnswer.id = nextAnswerId.getAndIncrement();
            newAnswer.pollId = pollId;
            newAnswer.questionId = questionId;
            newAnswer.choiceId = choiceId;
            newAnswer.userId = userId;
            newAnswer.answeredAt = Instant.now();
            pollAnswers.add(newAnswer);
        }

        // Check if all users answered ALL questions
        checkAndClosePollIfComplete(pollId);
    }

    private void checkAndClosePollIfComplete(long pollId) {
        Poll poll = polls.get(pollId);
        if (poll == null || !"ACTIVE".equals(poll.status)) return;

        int totalUsers = userService.countAll();
        int totalQuestions = poll.questions.size();

        if (totalUsers == 0 || totalQuestions == 0) return;

        List<Answer> pollAnswers = answers.get(pollId);
        if (pollAnswers == null) return;

        // Group answers by user and count how many questions each user answered
        Map<Long, Integer> userAnswerCounts = pollAnswers.stream()
                .collect(Collectors.groupingBy(
                        a -> a.userId,
                        Collectors.mapping(a -> a.questionId, Collectors.collectingAndThen(Collectors.toSet(), Set::size))
                ));

        // Count users who answered all questions
        long usersCompletedAll = userAnswerCounts.values().stream()
                .filter(count -> count >= totalQuestions)
                .count();

        if (usersCompletedAll >= totalUsers) {
            closePoll(pollId);
        }
    }

    public synchronized void closePoll(long pollId) {
        Poll poll = polls.get(pollId);
        if (poll != null && "ACTIVE".equals(poll.status)) {
            poll.status = "CLOSED";
            System.out.println("✅ Poll " + pollId + " has been closed.");

            // Notify listener
            if (listener != null) {
                listener.onPollClosed(poll);
            }
        }
    }

    private void closePollIfActive(long pollId) {
        closePoll(pollId); // Same logic, just ensures it's active before closing
    }

    /**
     * Compute results: for each question, map choices -> count and percentage.
     * Returns a map questionId -> list of (choiceText, count, percentage)
     */
    public Map<Long, List<Map<String, Object>>> computeResults(long pollId) {
        Poll poll = polls.get(pollId);
        if (poll == null) return Collections.emptyMap();

        List<Answer> pollAnswers = answers.getOrDefault(pollId, Collections.emptyList());
        Map<Long, List<Map<String, Object>>> results = new HashMap<>();

        for (Question q : poll.questions) {
            long qid = q.id;

            // Filter answers for this question
            List<Answer> questionAnswers = pollAnswers.stream()
                    .filter(a -> a.questionId == qid)
                    .collect(Collectors.toList());

            int total = questionAnswers.size();

            // Group answers by choice ID and count
            Map<Long, Long> choiceCounts = questionAnswers.stream()
                    .collect(Collectors.groupingBy(a -> a.choiceId, Collectors.counting()));

            List<Map<String, Object>> list = new ArrayList<>();

            // Iterate over all choices to ensure all are included, even those with 0 votes
            for (Choice choice : q.choices) {
                long choiceId = choice.id;
                int count = choiceCounts.getOrDefault(choiceId, 0L).intValue();
                double percentage = total == 0 ? 0.0 : (count * 100.0 / total);

                Map<String, Object> row = new HashMap<>();
                row.put("choiceText", choice.text);
                row.put("count", count);
                row.put("percentage", percentage);
                list.add(row);
            }

            // Sort by count descending (optional, but good for results display)
            list.sort((r1, r2) -> Integer.compare((Integer) r2.get("count"), (Integer) r1.get("count")));

            results.put(qid, list);
        }

        return results;
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
