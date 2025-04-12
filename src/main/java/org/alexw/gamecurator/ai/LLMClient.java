package org.alexw.gamecurator.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.alexw.gamecurator.misc.SettingsManager;

public class LLMClient {

    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai";
    private static final String CHAT_COMPLETIONS_ENDPOINT = "/chat/completions";
    private static final String MODEL = "gemini-2.0-flash";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(TIMEOUT)
            .build();

    private static final Gson gson = new Gson();

    private static class ChatMessage {
        String role;
        String content;

        ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static class GameRecommendations {
        String reasoning;
        List<String> answer;

        public String getReasoning() { return reasoning; }
        public List<String> getAnswer() { return answer; }

        @Override
        public String toString() {
            return "GameRecommendations{" +
                    "reasoning='" + reasoning + '\'' +
                    ", answer=" + answer +
                    '}';
        }
    }

    private static CompletableFuture<String> fetchChatCompletion(List<ChatMessage> messages, boolean requestJsonFormat) {
        String apiKey = SettingsManager.getLlmApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("LLM API Key is missing. Please configure it in the settings.");
            return CompletableFuture.failedFuture(new IOException("LLM API Key is not configured."));
        }

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", MODEL);
        requestBodyMap.put("messages", messages);
        requestBodyMap.put("stream", false); 

        if (requestJsonFormat) {

            requestBodyMap.put("response_format", Map.of("type", "json_object"));
        }

        String requestBodyJson = gson.toJson(requestBodyMap);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + CHAT_COMPLETIONS_ENDPOINT))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String responseBody = response.body();
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                            if (jsonResponse.has("choices") && jsonResponse.get("choices").isJsonArray()) {
                                JsonObject firstChoice = jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject();
                                if (firstChoice.has("message") && firstChoice.getAsJsonObject("message").has("content")) {
                                    return firstChoice.getAsJsonObject("message").get("content").getAsString();
                                }
                            }
                            throw new IOException("Unexpected response structure: 'choices[0].message.content' not found.");
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse successful response: " + e.getMessage(), e);
                        }
                    } else {

                        String errorMessage = "API error: Status code " + response.statusCode() + " - Body: " + responseBody;
                        System.err.println(errorMessage);
                        throw new RuntimeException(errorMessage);
                    }
                });
    }

    public static CompletableFuture<GameRecommendations> getGameRecommendations(String userPrompt) {

        List<ChatMessage> messages = List.of(
                new ChatMessage("system", "Your task is to provide game recommendations based on the user's game library. First reason about it with a long chain of thoughts, then output a list of five game name strings the user might like. Do not output games the user already has in their library. Output in JSON only as follows: {\"reasoning\":\"(Your chain of thoughts here)\",\"answer\":[\"An array of five game name strings\"]}"),
                new ChatMessage("user", userPrompt)
        );

        return fetchChatCompletion(messages, true)
                .thenApplyAsync(responseJsonString -> { 
                    try {

                        GameRecommendations recommendations = gson.fromJson(responseJsonString, GameRecommendations.class);
                        if (recommendations == null || recommendations.getAnswer() == null || recommendations.getReasoning() == null) {
                            System.err.println("Warning: Failed to parse response JSON into GameRecommendations. Raw response: " + responseJsonString);
                            throw new IOException("Parsed recommendations object or its fields are null.");
                        }
                        System.out.println("Successfully fetched and parsed recommendations.");
                        return recommendations;
                    } catch (Exception e) {
                        System.err.println("Error processing AI response: " + e.getMessage());
                        throw new RuntimeException("Failed to process AI recommendations", e);
                    }
                });
    }
}