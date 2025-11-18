package com.example.ui;

import com.example.bot.PollTelegramBot;
import com.example.client.OpenAiClient;
import com.example.model.Choice;
import com.example.model.Poll;
import com.example.model.Question;
import com.example.service.PollCloseListener;
import com.example.service.PollService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Simple Swing UI for poll creation and result viewing.
 * - create manually or via OpenAI topic
 * - view results with percentages
 * - this is minimal and can be extended (charts, nice layout)
 */
public class SwingUI extends JFrame implements PollCloseListener {
    private final PollService pollService;
    private final OpenAiClient openAiClient;
    private PollTelegramBot bot;

    private final JTextField titleField = new JTextField(30);
    private final JTextField delayField = new JTextField("5", 5); // minutes
    private final JTextArea topicArea = new JTextArea(2, 30);
    private final JButton genBtn = new JButton("Generate with ChatGPT");
    private final JButton createBtn = new JButton("Create Poll (Manual)");
    private final JButton viewResultsBtn = new JButton("View Results");
    private final JButton templateBtn = new JButton("Load Template Poll"); // New button for template

    public SwingUI(PollService pollService, OpenAiClient openAiClient) {
        super("Poll Creator & Results Viewer");
        this.pollService = pollService;
        this.openAiClient = openAiClient;

        // Register this UI as a listener for poll closing events
        this.pollService.setPollCloseListener(this);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Create main panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Title section
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titlePanel.add(new JLabel("Poll Title:"));
        titlePanel.add(titleField);
        mainPanel.add(titlePanel);

        // Delay section
        JPanel delayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        delayPanel.add(new JLabel("Delay (minutes):"));
        delayPanel.add(delayField);
        mainPanel.add(delayPanel);

        // Topic section
        JPanel topicPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topicPanel.add(new JLabel("Topic for ChatGPT:"));
        topicPanel.add(new JScrollPane(topicArea));
        mainPanel.add(topicPanel);

        // Buttons section
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(genBtn);
        buttonPanel.add(createBtn);
        buttonPanel.add(templateBtn); // Add template button
        buttonPanel.add(viewResultsBtn);
        mainPanel.add(buttonPanel);

        add(mainPanel, BorderLayout.CENTER);

        genBtn.addActionListener(this::onGenerate);
        createBtn.addActionListener(this::onCreateManual);
        templateBtn.addActionListener(this::onLoadTemplate); // Add listener for template
        viewResultsBtn.addActionListener(this::onViewResults);

        setSize(750, 350);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void setBot(PollTelegramBot bot) {
        this.bot = bot;
    }

    private void onGenerate(ActionEvent e) {
        String topic = topicArea.getText().trim();
        if (topic.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a topic for ChatGPT");
            return;
        }

        try {
            List<OpenAiClient.OpenAiQuestion> aiQuestions = openAiClient.generateSurveyQuestions(topic);

            if (aiQuestions.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No questions generated. Please try again.");
                return;
            }

            // Limit to 3 questions as per requirements
            if (aiQuestions.size() > 3) {
                aiQuestions = aiQuestions.subList(0, 3);
            }

            List<Question> questions = new ArrayList<>();
            for (OpenAiClient.OpenAiQuestion aiQ : aiQuestions) {
                Question q = new Question();
                q.text = aiQ.getQuestionText();

                List<String> aiChoices = aiQ.getChoices();
                // Ensure 2-4 choices
                if (aiChoices.size() < 2) {
                    aiChoices.add("Other");
                }
                if (aiChoices.size() > 4) {
                    aiChoices = aiChoices.subList(0, 4);
                }

                for (String choiceText : aiChoices) {
                    Choice c = new Choice();
                    c.text = choiceText;
                    q.choices.add(c);
                }
                questions.add(q);
            }

            createAndSendPoll(titleField.getText().isEmpty() ? ("Poll about " + topic) : titleField.getText(), questions);

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private void onCreateManual(ActionEvent e) {
        try {
            String numQStr = JOptionPane.showInputDialog(this, "How many questions? (1-3):");
            if (numQStr == null) return;
            int numQuestions = Integer.parseInt(numQStr);
            if (numQuestions < 1 || numQuestions > 3) {
                JOptionPane.showMessageDialog(this, "Number of questions must be 1-3");
                return;
            }

            List<Question> questions = new ArrayList<>();

            for (int i = 0; i < numQuestions; i++) {
                Question q = showQuestionInput(i + 1);
                if (q == null) return;
                questions.add(q);
            }

            createAndSendPoll(titleField.getText().isEmpty() ? "Manual Poll" : titleField.getText(), questions);

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private Question showQuestionInput(int index) {
        JTextField questionText = new JTextField(30);
        JTextField choice1 = new JTextField(20);
        JTextField choice2 = new JTextField(20);
        JTextField choice3 = new JTextField(20);
        JTextField choice4 = new JTextField(20);

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Question " + index + " Text:"));
        panel.add(questionText);
        panel.add(new JLabel("Choices (Min 2, Max 4):"));
        panel.add(new JLabel("Choice 1:"));
        panel.add(choice1);
        panel.add(new JLabel("Choice 2:"));
        panel.add(choice2);
        panel.add(new JLabel("Choice 3 (Optional):"));
        panel.add(choice3);
        panel.add(new JLabel("Choice 4 (Optional):"));
        panel.add(choice4);

        int result = JOptionPane.showConfirmDialog(this, panel, "Enter Question " + index + " Details",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String qText = questionText.getText().trim();
            List<String> rawChoices = Arrays.asList(choice1.getText(), choice2.getText(), choice3.getText(), choice4.getText());

            List<String> validChoices = rawChoices.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            if (qText.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Question text cannot be empty.");
                return showQuestionInput(index); // Re-show dialog
            }

            if (validChoices.size() < 2 || validChoices.size() > 4) {
                JOptionPane.showMessageDialog(this, "A question must have between 2 and 4 choices.");
                return showQuestionInput(index); // Re-show dialog
            }

            Question q = new Question();
            q.text = qText;
            for (String text : validChoices) {
                Choice c = new Choice();
                c.text = text;
                q.choices.add(c);
            }
            return q;
        }
        return null;
    }

    private void onLoadTemplate(ActionEvent e) {
        // Template Poll: Programming Preferences
        String title = "Programming Language Preferences Survey";
        List<Question> questions = new ArrayList<>();

        // Question 1
        Question q1 = new Question();
        q1.text = "Which programming language do you use most frequently?";
        q1.choices.add(new Choice() {{ text = "Java"; }});
        q1.choices.add(new Choice() {{ text = "Python"; }});
        q1.choices.add(new Choice() {{ text = "JavaScript"; }});
        questions.add(q1);

        // Question 2
        Question q2 = new Question();
        q2.text = "What is your preferred IDE/Editor?";
        q2.choices.add(new Choice() {{ text = "IntelliJ IDEA"; }});
        q2.choices.add(new Choice() {{ text = "VS Code"; }});
        q2.choices.add(new Choice() {{ text = "Eclipse"; }});
        questions.add(q2);

        // Question 3
        Question q3 = new Question();
        q3.text = "How do you prefer to learn new technologies?";
        q3.choices.add(new Choice() {{ text = "Online Courses (e.g., Coursera)"; }});
        q3.choices.add(new Choice() {{ text = "Documentation and Books"; }});
        q3.choices.add(new Choice() {{ text = "Hands-on Projects"; }});
        questions.add(q3);

        // Set title field and topic area for user convenience
        titleField.setText(title);
        topicArea.setText("Programming preferences among developers");

        try {
            createAndSendPoll(title, questions);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private void createAndSendPoll(String title, List<Question> questions) throws Exception {
        int delay = Integer.parseInt(delayField.getText());
        Poll poll = pollService.createPoll(title, 1L, questions, delay);

        if (bot != null) {
            bot.sendPollToAllUsers(poll);
        }

        JOptionPane.showMessageDialog(this, "Poll created and sent to all users!");
    }

    private void onViewResults(ActionEvent e) {
        try {
            Poll poll = pollService.getActivePoll();
            if (poll == null) {
                JOptionPane.showMessageDialog(this, "No active poll found.");
                return;
            }

            displayResults(poll);

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error viewing results: " + ex.getMessage());
        }
    }

    /**
     * Helper method to display poll results in a new JFrame.
     */
    private void displayResults(Poll poll) {
        try {
            Map<Long, List<Map<String, Object>>> results = pollService.computeResults(poll.id);

            JFrame resultsFrame = new JFrame("Poll Results: " + poll.title);
            resultsFrame.setLayout(new BorderLayout());

            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            for (Question q : poll.questions) {
                JPanel questionPanel = new JPanel(new BorderLayout());
                questionPanel.setBorder(BorderFactory.createTitledBorder("Question: " + q.text));

                List<Map<String, Object>> questionResults = results.get(q.id);
                if (questionResults != null && !questionResults.isEmpty()) {
                    String[] columnNames = {"Choice", "Votes", "Percentage"};
                    DefaultTableModel model = new DefaultTableModel(columnNames, 0);

                    for (Map<String, Object> row : questionResults) {
                        String choiceText = (String) row.get("choiceText");
                        int count = (Integer) row.get("count");
                        double percentage = (Double) row.get("percentage");
                        model.addRow(new Object[]{choiceText, count, String.format("%.1f%%", percentage)});
                    }

                    JTable table = new JTable(model);
                    table.setEnabled(false);
                    JScrollPane scrollPane = new JScrollPane(table);
                    questionPanel.add(scrollPane, BorderLayout.CENTER);
                } else {
                    questionPanel.add(new JLabel("No votes yet"), BorderLayout.CENTER);
                }

                mainPanel.add(questionPanel);
            }

            JScrollPane mainScroll = new JScrollPane(mainPanel);
            resultsFrame.add(mainScroll, BorderLayout.CENTER);

            resultsFrame.setSize(600, 500);
            resultsFrame.setLocationRelativeTo(this);
            resultsFrame.setVisible(true);

            JOptionPane.showMessageDialog(this, "Poll '" + poll.title + "' has closed. Results are now displayed.");

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error displaying results: " + ex.getMessage());
        }
    }

    @Override
    public void onPollClosed(Poll closedPoll) {
        // Ensure UI update happens on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            displayResults(closedPoll);
        });
    }
}
