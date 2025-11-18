package com.example.bot;

import com.example.model.Choice;
import com.example.model.Poll;
import com.example.model.Question;
import com.example.service.PollService;
import com.example.service.UserService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Telegram bot implementation:
 * - registers users on "/start"/"hi"/"היי"
 * - notifies others when new user joins
 * - sends polls to all users
 * - handles callback votes (expects callback_data format "vote:pollId:questionId:choiceId")
 *
 * Note: For production handle rate limits and message batching.
 */
public class PollTelegramBot extends TelegramLongPollingBot {
    private final PollService pollService;
    private final UserService userService;
    // =================================================================================
    // ⚠️⚠️⚠️ ATTENTION: You must manually edit the following values before building the project ⚠️⚠️⚠️
    // =================================================================================
    private static final String BOT_USERNAME = "Task_Survey_bot";
    private static final String BOT_TOKEN = "8300719029:AAHGiPOGOh3hsh5bB2bE7Zh9VBuRD23AhM4";
    // =================================================================================


    public PollTelegramBot(PollService pollService, UserService userService) {
        this.pollService = pollService;
        this.userService = userService;
        
        // Check if user has updated the values
        if (BOT_TOKEN.equals("YOUR_BOT_TOKEN_HERE") || BOT_USERNAME.equals("YOUR_BOT_USERNAME_HERE")) {
            System.err.println("⚠️ ERROR: Please update BOT_TOKEN and BOT_USERNAME constants in PollTelegramBot.java!");
        }
    }

    @Override
    public String getBotUsername() { 
        return BOT_USERNAME; 
    }

    @Override
    public String getBotToken() { 
        return BOT_TOKEN; 
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Message m = update.getMessage();
                String txt = m.getText().trim();
                long tgid = m.getFrom().getId();
                String name = m.getFrom().getFirstName();
                
                // Accept /start, start, hi, Hi, היי
                if (txt.equalsIgnoreCase("/start") || 
                    txt.equalsIgnoreCase("start") || 
                    txt.equalsIgnoreCase("hi") || 
                    txt.equalsIgnoreCase("היי")) {
                    
                    userService.registerIfNotExists(tgid, name);
                    
                    // Send welcome message to the new user
                    SendMessage welcome = new SendMessage();
                    welcome.setChatId(String.valueOf(tgid));
                    welcome.setText("Welcome to the Poll Community! 🎉\nYou have been registered successfully.");
                    try { execute(welcome); } catch (Exception ex) { ex.printStackTrace(); }
                    
                    // Notify all users about new member
                    int communitySize = userService.countAll();
                    userService.listAll().forEach(u -> {
                        if (u.telegramId != tgid) { // Don't send to the new user again
                            SendMessage sm = new SendMessage();
                            sm.setChatId(String.valueOf(u.telegramId));
                            sm.setText("📢 New member joined: " + name + "\n👥 Community size: " + communitySize);
                            try { execute(sm); } catch (Exception ex) { /* ignore */ }
                        }
                    });
                }
            } else if (update.hasCallbackQuery()) {
                CallbackQuery cb = update.getCallbackQuery();
                String data = cb.getData();
                long tgid = cb.getFrom().getId();
                AnswerCallbackQuery ack = new AnswerCallbackQuery();
                ack.setCallbackQueryId(cb.getId());
                try {
                    pollService.recordVote(data, tgid);
                    ack.setText("✅ Vote recorded. Thank you!");
                } catch (Exception ex) {
                    ack.setText("❌ Error: " + ex.getMessage());
                }
                try { execute(ack); } catch (Exception ignored) {}
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Send poll to all registered users
     */
    public void sendPollToAllUsers(Poll poll) {
        if (poll == null || poll.questions == null || poll.questions.isEmpty()) {
            System.err.println("⚠️ Cannot send empty poll");
            return;
        }
        
        List<Long> userIds = new ArrayList<>();
        userService.listAll().forEach(u -> userIds.add(u.telegramId));
        
        System.out.println("📤 Sending poll to " + userIds.size() + " users...");
        
        for (Long userId : userIds) {
            try {
                // Send poll title
                SendMessage titleMsg = new SendMessage();
                titleMsg.setChatId(String.valueOf(userId));
                titleMsg.setText("📊 *New Poll: " + poll.title + "*\n\nPlease answer all questions:");
                titleMsg.setParseMode("Markdown");
                execute(titleMsg);
                
                // Send each question with inline keyboard
                for (Question q : poll.questions) {
                    SendMessage qMsg = new SendMessage();
                    qMsg.setChatId(String.valueOf(userId));
                    qMsg.setText("❓ " + q.text);
                    
                    // Create inline keyboard with choices
                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                    
                    for (Choice choice : q.choices) {
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText(choice.text);
                        button.setCallbackData("vote:" + poll.id + ":" + q.id + ":" + choice.id);
                        
                        List<InlineKeyboardButton> row = new ArrayList<>();
                        row.add(button);
                        keyboard.add(row);
                    }
                    
                    markup.setKeyboard(keyboard);
                    qMsg.setReplyMarkup(markup);
                    
                    execute(qMsg);
                    
                    // Small delay to avoid rate limiting
                    Thread.sleep(100);
                }
                
            } catch (Exception e) {
                System.err.println("❌ Failed to send poll to user " + userId + ": " + e.getMessage());
            }
        }
        
        System.out.println("✅ Poll sent to all users successfully!");
    }
}
