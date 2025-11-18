package com.example;

import com.example.bot.PollTelegramBot;
// import com.example.db.DatabaseManager; // Removed DB import
import com.example.service.PollService;
import com.example.service.UserService;
import com.example.client.OpenAiClient;
import com.example.ui.SwingUI;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.swing.*;

/**
 * Main application entry point.
 * - Initializes services, Telegram bot, and Swing UI.
 * - Asks the user to input their STUDENT ID securely before starting.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("📘 Telegram Survey Application starting...");

        // === Ask for Student ID ===
        String studentId = askForStudentId();
        if (studentId == null || studentId.isBlank()) {
            System.err.println("⚠️ No student ID entered. Exiting application.");
            return;
        }

        System.out.println("✅ Student ID received successfully (hidden).");

        // === Initialize Services (In-Memory) ===
        UserService userService = new UserService();
        PollService pollService = new PollService(userService);
        System.out.println("✅ Services initialized.");

        // === Initialize OpenAI Client with the student ID ===
        OpenAiClient openAiClient = new OpenAiClient(studentId);
        System.out.println("✅ OpenAI Client initialized.");

        // === Start Telegram Bot ===
        PollTelegramBot bot = new PollTelegramBot(pollService, userService);
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);
        System.out.println("🤖 Telegram Bot registered successfully.");

        // === Start Swing UI (on EDT) ===
        javax.swing.SwingUtilities.invokeLater(() -> {
            SwingUI ui = new SwingUI(pollService, openAiClient);
            ui.setBot(bot); // Pass bot reference to UI for sending polls
            System.out.println("🖥️ Swing UI started successfully.");
        });

        System.out.println("🚀 Application started successfully.");
        System.out.println("\n📋 Instructions:");
        System.out.println("1. Users should send /start or 'hi' to the bot to register");
        System.out.println("2. At least 3 users must be registered to create a poll");
        System.out.println("3. Use the Swing UI to create polls (manual or AI-generated)");
        System.out.println("4. Polls will be automatically sent to all registered users");
        System.out.println("5. View results in the Swing UI after users vote");
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down application...");
            pollService.shutdown();
            System.out.println("✅ Application shutdown complete.");
        }));
    }

    /**
     * Opens a small Swing input dialog to safely ask for the student's ID.
     */
    private static String askForStudentId() {
        JPanel panel = new JPanel();
        JLabel label = new JLabel("Enter your Student ID:");
        JPasswordField pass = new JPasswordField(10); // hides the input
        panel.add(label);
        panel.add(pass);

        int option = JOptionPane.showConfirmDialog(
                null,
                panel,
                "Student Verification",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (option == JOptionPane.OK_OPTION) {
            return new String(pass.getPassword()).trim();
        }
        return null;
    }
}
