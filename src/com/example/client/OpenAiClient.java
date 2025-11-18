package com.example.client;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client for communicating with the university's ChatGPT API (https://app.seker.live/fm1/).
 * This version replaces the OpenAI API with the local API used in the course.
 */
public class OpenAiClient {

    private static final String API_URL = "https://app.seker.live/fm1/send-message";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;
    private final String studentId; // תעודת זהות

    // Custom Error Codes
    public static final int ERROR_NO_STUDENT_ID = 3000;
    public static final int ERROR_INVALID_STUDENT_ID = 3001;
    public static final int ERROR_RATE_LIMIT = 3002;
    public static final int ERROR_NO_TEXT_PROVIDED = 3003;
    public static final int ERROR_GENERAL_API_FAILURE = 3005;

    public OpenAiClient(String studentId) {
        if (studentId == null || studentId.isEmpty()) {
            // This case should be handled before calling the constructor, but we'll keep the check.
            throw new IllegalArgumentException("Student ID must not be empty! (Code: " + ERROR_NO_STUDENT_ID + ")");
        }
        this.studentId = studentId;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Sends a prompt to the university API and gets a textual response.
     * Throws an IOException with a custom error code in the message.
     */
    public String sendMessage(String text) throws IOException {
        // Check for ERROR_NO_TEXT_PROVIDED (3003)
        if (text == null || text.isBlank()) {
            throw new IOException("No text provided for API call. (Code: " + ERROR_NO_TEXT_PROVIDED + ")");
        }

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("id", studentId);
        jsonBody.put("text", text);

        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            System.out.println("[API RAW RESPONSE] " + responseBody);

            if (!response.isSuccessful()) {
                // Check for API error codes in the response body
                try {
                    JSONObject errorJson = new JSONObject(responseBody);
                    if (errorJson.has("error_code")) {
                        int errorCode = errorJson.getInt("error_code");
                        String errorMsg = errorJson.optString("message", "Unknown error");

                        // Map known API errors to custom codes
                        if (errorCode == 401) { // Example: Unauthorized/Invalid ID
                            throw new IOException("API error " + errorCode + ": " + errorMsg + " (Code: " + ERROR_INVALID_STUDENT_ID + ")");
                        } else if (errorCode == 429) { // Example: Rate Limit Exceeded
                            throw new IOException("API error " + errorCode + ": " + errorMsg + " (Code: " + ERROR_RATE_LIMIT + ")");
                        } else {
                            // General API error
                            throw new IOException("API error " + errorCode + ": " + errorMsg + " (Code: " + ERROR_GENERAL_API_FAILURE + ")");
                        }
                    }
                } catch (Exception e) {
                    // If not JSON error, throw generic error with code 3005
                    throw new IOException("Unexpected code " + response.code() + " with body: " + responseBody + " (Code: " + ERROR_GENERAL_API_FAILURE + ")");
                }
                // If response is not successful but no error_code was found in JSON
                throw new IOException("Unexpected code " + response.code() + " with body: " + responseBody + " (Code: " + ERROR_GENERAL_API_FAILURE + ")");
            }

            // Try to parse JSON response
            try {
                JSONObject jsonResponse = new JSONObject(responseBody);
                if (jsonResponse.has("response")) {
                    return jsonResponse.getString("response");
                }
            } catch (Exception e) {
                // If not JSON, return plain text
            }

            return responseBody;
        } catch (IOException e) {
            // Re-throw the exception, but catch general network errors
            if (e.getMessage().contains("Code:")) {
                throw e; // Re-throw if it already contains a custom code
            }
            // General network/IO error (e.g., timeout, connection refused)
            throw new IOException(e.getMessage() + " (Code: " + ERROR_GENERAL_API_FAILURE + ")");
        }
    }

    /**
     * Generates 1–3 survey questions using the course API.
     * The API is not a true OpenAI model, so we prompt it directly.
     */
    public List<OpenAiQuestion> generateSurveyQuestions(String topic) throws IOException {
        // The API is expected to return the JSON directly based on the prompt.
        // The prompt is now just the topic, as the API is supposed to handle the JSON formatting.
        String prompt = "Generate a survey about \"" + topic + "\" with 1-3 questions, each having 2-4 choices. Return the result as a JSON array of objects, where each object has 'question' and 'answers' fields.";

        String reply = sendMessage(prompt);

        // Try parsing into OpenAiQuestion list
        List<OpenAiQuestion> result = new ArrayList<>();
        try {
            JSONArray jsonArr = new JSONArray(reply);
            for (int i = 0; i < jsonArr.length(); i++) {
                JSONObject obj = jsonArr.getJSONObject(i);
                String question = obj.optString("question", "");
                JSONArray answersArr = obj.optJSONArray("answers");
                List<String> answers = new ArrayList<>();
                if (answersArr != null) {
                    for (int j = 0; j < answersArr.length(); j++) {
                        answers.add(answersArr.getString(j));
                    }
                }
                if (!question.isBlank() && !answers.isEmpty()) {
                    result.add(new OpenAiQuestion(question, answers));
                }
            }
        } catch (Exception e) {
            System.err.println("[WARNING] Could not parse JSON: " + e.getMessage());
            // fallback: if JSON parsing fails, throw an exception
            throw new IOException("API response is not a valid JSON array: " + reply + " (Code: " + ERROR_GENERAL_API_FAILURE + ")");
        }

        return result;
    }

    /**
     * Simple data structure for questions.
     */
    public static class OpenAiQuestion {
        private final String questionText;
        private final List<String> choices;

        public OpenAiQuestion(String questionText, List<String> choices) {
            this.questionText = questionText;
            this.choices = choices;
        }

        public String getQuestionText() {
            return questionText;
        }

        public List<String> getChoices() {
            return choices;
        }

        @Override
        public String toString() {
            return "OpenAiQuestion{" +
                    "questionText='" + questionText + '\'' +
                    ", choices=" + choices +
                    '}';
        }
    }
}