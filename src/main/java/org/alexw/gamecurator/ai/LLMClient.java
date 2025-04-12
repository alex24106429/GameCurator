package org.alexw.gamecurator.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.alexw.gamecurator.misc.CacheManager;
import org.alexw.gamecurator.misc.SettingsManager; // Import SettingsManager


public class LLMClient {

    // --- Configuration ---
    // Updated for Gemini API (OpenAI-compatible endpoint)
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai";
    private static final String CHAT_COMPLETIONS_ENDPOINT = "/chat/completions";
    private static final String MODEL = "gemini-2.0-flash"; // Updated model
    private static final Duration TIMEOUT = Duration.ofSeconds(60); // Request timeout

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(TIMEOUT)
            .build();

    private static final Gson gson = new Gson();

    // Represents a single message in the chat history
    public static class ChatMessage {
        String role;
        String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
        // Getters are needed for Gson serialization if fields are private
        public String getRole() { return role; }
        public String getContent() { return content; }
    }

    // Represents the expected JSON structure returned by getGameRecommendations
    public static class GameRecommendations {
        String reasoning;
        List<String> answer;

        // Getters
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


    /**
     * Sends a chat completion request to the LLM API (now configured for Gemini).
     *
     * @param messages The list of ChatMessage objects representing the conversation history.
     * @param options  Additional options for the API call (e.g., "format": "json").
     * @return A CompletableFuture containing the content of the assistant's response as a String.
     *         Completes exceptionally if the API key is missing or an API error occurs.
     */
    public static CompletableFuture<String> fetchChatCompletion(List<ChatMessage> messages, Map<String, Object> options) {
        String apiKey = SettingsManager.getLlmApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("LLM API Key is missing. Please configure it in the settings.");
            return CompletableFuture.failedFuture(new IOException("LLM API Key is not configured."));
        }

        // Build the request body map
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", MODEL);
        requestBodyMap.put("messages", messages);
        requestBodyMap.put("stream", false);

        // Add filtered options and handle structured output request
        if (options != null) {
            options.forEach((key, value) -> {
                if (!key.equals("model") && !key.equals("messages")) {
                    if (key.equals("format") && "json".equals(value)) {
                        // Use standard OpenAI JSON mode for structured output
                        requestBodyMap.put("response_format", Map.of("type", "json_object"));
                    } else {
                        // Add other valid options here if needed in the future
                        // requestBodyMap.put(key, value);
                        System.out.println("Ignoring unsupported option: " + key); // Or handle other options
                    }
                }
            });
        }

        String requestBodyJson = gson.toJson(requestBodyMap);
        // System.out.println("Request Body JSON: " + requestBodyJson); // Debugging

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + CHAT_COMPLETIONS_ENDPOINT)) // Use combined URL
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey) // Use the fetched API key
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String responseBody = response.body();
                    // System.out.println("Response Status Code: " + response.statusCode()); // Debugging
                    // System.out.println("Response Body: " + responseBody); // Debugging

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            // Parse the JSON response to extract the content
                            // This structure is standard for OpenAI compatible APIs
                            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                            if (jsonResponse.has("choices") && jsonResponse.get("choices").isJsonArray()) {
                                JsonArray choices = jsonResponse.getAsJsonArray("choices");
                                if (choices.size() > 0 && choices.get(0).isJsonObject()) {
                                    JsonObject firstChoice = choices.get(0).getAsJsonObject();
                                    if (firstChoice.has("message") && firstChoice.get("message").isJsonObject()) {
                                        JsonObject message = firstChoice.getAsJsonObject("message");
                                        if (message.has("content") && message.get("content").isJsonPrimitive()) {
                                            return message.get("content").getAsString();
                                        }
                                    }
                                }
                            }
                            // If structure is unexpected, throw an error
                            throw new IOException("Unexpected response structure: 'choices[0].message.content' not found.");
                        } catch (Exception e) {
                            // Handle JSON parsing errors
                            throw new RuntimeException("Failed to parse successful response: " + e.getMessage(), e);
                        }
                    } else {
                        // Handle API errors
                        String errorMessage = "API error: Status code " + response.statusCode();
                        try {
                            // Try to parse error details from the response body (common OpenAI/Gemini structures)
                            JsonObject errorJson = JsonParser.parseString(responseBody).getAsJsonObject();
                            if (errorJson.has("error") && errorJson.get("error").isJsonObject()) {
                                JsonObject errorObj = errorJson.getAsJsonObject("error");
                                if (errorObj.has("message") && errorObj.get("message").isJsonPrimitive()) {
                                    errorMessage += " - " + errorObj.get("message").getAsString();
                                }
                            } else if (errorJson.has("detail") && errorJson.get("detail").isJsonPrimitive()) {
                                errorMessage += " - " + errorJson.get("detail").getAsString();
                            } else if (errorJson.has("message") && errorJson.get("message").isJsonPrimitive()) { // Another possible error structure
                                errorMessage += " - " + errorJson.get("message").getAsString();
                            } else {
                                errorMessage += " - " + responseBody; // Fallback to raw body
                            }
                        } catch (Exception parseEx) {
                            // If error body isn't valid JSON or structure is different
                            errorMessage += " - Failed to parse error response body: " + responseBody;
                        }
                        System.err.println(errorMessage); // Log the full error
                        // Throw a specific exception or RuntimeException wrapped in CompletionException
                        throw new RuntimeException(errorMessage);
                    }
                });
    }


    /**
     * Generates game recommendations based on the user's library using the configured LLM API.
     * Uses CacheManager to cache results.
     *
     * @param libraryItemIds A Set of game IDs in the user's library.
     * @return A CompletableFuture containing the GameRecommendations object.
     *         Completes exceptionally if the API key is missing or an API error occurs.
     */
    public static CompletableFuture<GameRecommendations> getGameRecommendations(Set<Integer> libraryItemIds) {
        // Check for API key before proceeding (optional, fetchChatCompletion also checks)
        String apiKey = SettingsManager.getLlmApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
             System.err.println("LLM API Key is missing for recommendations. Please configure it in the settings.");
             return CompletableFuture.failedFuture(new IOException("LLM API Key is not configured."));
        }

        // 1. Calculate Cache Key (Using simple sum like JS, but note its weakness)
        long libraryHash = 0;
        // Sort IDs for consistency before summing (slightly better than unsorted sum)
        List<Integer> sortedIds = new ArrayList<>(libraryItemIds);
        java.util.Collections.sort(sortedIds);
        for (int id : sortedIds) {
            libraryHash += id;
        }
        String cacheKey = "recommendations_" + libraryHash + "_" + MODEL; // Add model to cache key

        // 2. Check Cache
        String cachedJson = CacheManager.get(cacheKey);
        if (cachedJson != null) {
            System.out.println("Returning cached recommendations for key: " + cacheKey);
            try {
                GameRecommendations cachedRecs = gson.fromJson(cachedJson, GameRecommendations.class);
                if (cachedRecs != null && cachedRecs.getAnswer() != null && cachedRecs.getReasoning() != null) {
                    return CompletableFuture.completedFuture(cachedRecs);
                } else {
                    System.err.println("Cached data for " + cacheKey + " is invalid. Fetching fresh.");
                    CacheManager.remove(cacheKey); // Remove invalid cache entry
                }
            } catch (Exception e) {
                System.err.println("Error parsing cached recommendations: " + e.getMessage() + ". Fetching fresh.");
                CacheManager.remove(cacheKey); // Remove corrupted cache entry
            }
        }

        // 3. Build Prompt if not cached
        StringBuilder promptBuilder = new StringBuilder();
        int gamesProcessed = 0;
        for (int gameId : libraryItemIds) {
            String gameDataJson = CacheManager.get("gameData_" + gameId);
            if (gameDataJson != null) {
                try {
                    JsonObject game = JsonParser.parseString(gameDataJson).getAsJsonObject();
                    String name = game.has("name") ? game.get("name").getAsString() : "Unknown Title";

                    String genres = "Unknown Genres";
                    if (game.has("genres") && game.get("genres").isJsonArray()) {
                        genres = game.getAsJsonArray("genres").asList().stream()
                                .filter(JsonElement::isJsonObject)
                                .map(g -> g.getAsJsonObject().has("name") ? g.getAsJsonObject().get("name").getAsString() : "")
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.joining(" "));
                    }
                    if (genres.isEmpty()) genres = "Unknown Genres"; // Handle case where genre names are missing

                    promptBuilder.append("Title: ").append(name).append("\n");
                    promptBuilder.append("Genres: ").append(genres).append("\n---\n");
                    gamesProcessed++;
                } catch (Exception e) {
                    System.err.println("Error processing cached game data for ID " + gameId + ": " + e.getMessage());
                    // Skip this game in the prompt
                }
            } else {
                System.err.println("Could not find cached game data for ID " + gameId + " in library.");
                // Skip this game in the prompt
            }
        }

        // Check if we could build any prompt content
        if (gamesProcessed == 0) {
            System.err.println("No valid game data found in cache for the library. Cannot generate recommendations.");
            // Return a future that completes exceptionally or with an empty/error state
            return CompletableFuture.failedFuture(new IOException("Could not build prompt, no valid library game data found in cache."));
        }

        String userPrompt = promptBuilder.toString();

        // 4. Define Messages for the API
        // The system prompt already strongly requests JSON output.
        // The example conversation helps guide the model.
        List<ChatMessage> messages = List.of(
                new ChatMessage("system", "Your task is to provide game recommendations based on the user's game library. First reason about it with a long chain of thoughts, then output a list of five game name strings the user might like. Do not output games the user already has in their library. Output in JSON only as follows: {\"reasoning\":\"(Your chain of thoughts here)\",\"answer\":[\"An array of five game name strings\"]}"),
                new ChatMessage("user", "Title: Grand Theft Auto V \nGenres: Action \n--- \nTitle: The Witcher 3: Wild Hunt \nGenres: Action RPG \n--- \nTitle: Left 4 Dead 2 \nGenres: Action Shooter \n--- \nTitle: Fallout 4 \nGenres: Action RPG \n---\n"),
                new ChatMessage("assistant", "{\"reasoning\": \"Okay, so the user has these four games in their library: Grand Theft Auto V, The Witcher 3, Left 4 Dead 2, and Fallout 4. I need to come up with five game recommendations based on these. Let me start by analyzing each game's genre and see what common themes or elements they might share. First, GTA V is an Action game, open-world with a lot of driving, shooting, and a story with multiple characters. The Witcher 3 is an Action RPG, which combines deep storytelling, character progression, and a vast open world. Left 4 Dead 2 is an Action Shooter, focusing on cooperative gameplay, survival against hordes of zombies, and fast-paced combat. Fallout 4 is another Action RPG, post-apocalyptic setting, with exploration, crafting, and branching storylines. Looking at the genres, there's a mix of action-oriented games, some with RPG elements, open-world exploration, and cooperative play. The user might enjoy games that offer a blend of action, storytelling, and either open-world exploration or team-based gameplay. For GTA V, similar games could be other open-world action games like Red Dead Redemption 2, which is from the same developer and offers a similar structure but in a Western setting. Saints Row IV might also be a good fit with its over-the-top action and humor. The Witcher 3 and Fallout 4 both are Action RPGs with rich narratives. Games like Skyrim (another Bethesda title with deep RPG elements) or Horizon Zero Dawn (open-world, story-driven with RPG mechanics) could be good. Maybe Cyberpunk 2077, since it's from CD Projekt Red like The Witcher and has RPG elements. Left 4 Dead 2's co-op shooter aspect suggests games that emphasize teamwork against waves of enemies. Back 4 Blood is a spiritual successor made by some of the same developers. World War Z is another co-op zombie shooter. Maybe Destiny 2 for its PvE content and shooter mechanics, though it's more of a looter-shooter. I should also consider cross-genre recommendations. Maybe a game that combines elements of action RPG and open-world, like Assassin's Creed Valhalla. Or Borderlands series for its shooter elements and RPG loot system. Also, maybe Days Gone for an open-world zombie game with a story. Now, compiling these thoughts into five recommendations. Let's see: 1. Red Dead Redemption 2: Same developer as GTA V, open-world action, story-rich. Fits the action and open-world aspect. 2. Cyberpunk 2077: Action RPG from CD Projekt, similar to The Witcher 3's depth, futuristic setting but still story-driven. 3. Borderlands 3: Combines shooter elements with RPG loot, co-op like Left 4 Dead 2, humor and action. 4. Days Gone: Open-world zombie survival, story-driven, action elements similar to Fallout and Left 4 Dead. 5. Horizon Zero Dawn: Open-world action RPG with a strong narrative, similar to The Witcher and Fallout. Alternatively, Back 4 Blood instead of Days Gone for the co-op zombie aspect. But Days Gone adds variety. Let me check if these cover the different aspects: open-world action, RPG elements, co-op shooters. Yeah, that seems balanced. Maybe include one more RPG if possible, but five is the limit. So these five should work.\",\"answer\":[\"Red Dead Redemption 2\",\"Cyberpunk 2077\",\"Borderlands 3\",\"Horizon Zero Dawn\",\"Back 4 Blood\"]}"),
                new ChatMessage("user", userPrompt)
        );

        // 5. Define Options for the API Call
        Map<String, Object> options = Map.of("format", "json"); // Request JSON output format

        // 6. Call fetchChatCompletion and process the result
        return fetchChatCompletion(messages, options)
                .thenApplyAsync(responseJsonString -> { // Use async variant if parsing is potentially long
                    try {
                        // 7. Cache the raw response
                        CacheManager.put(cacheKey, responseJsonString);

                        // 8. Parse the JSON string into GameRecommendations object
                        GameRecommendations recommendations = gson.fromJson(responseJsonString, GameRecommendations.class);
                        if (recommendations == null || recommendations.getAnswer() == null || recommendations.getReasoning() == null) {
                            // If JSON mode was requested but parsing failed, the model might not have adhered strictly
                            System.err.println("Warning: Failed to parse response JSON into GameRecommendations. Raw response: " + responseJsonString);
                            throw new IOException("Parsed recommendations object or its fields are null. Raw response: " + responseJsonString);
                        }
                        System.out.println("Successfully fetched and parsed recommendations.");
                        return recommendations;
                    } catch (Exception e) {
                        // Handle parsing errors or caching errors
                        System.err.println("Error processing or caching AI response: " + e.getMessage());
                        // Ensure bad data isn't left in cache if parsing failed
                        CacheManager.remove(cacheKey);
                        // Rethrow as a RuntimeException to fail the CompletableFuture
                        throw new RuntimeException("Failed to process or cache AI recommendations", e);
                    }
                });
    }
}