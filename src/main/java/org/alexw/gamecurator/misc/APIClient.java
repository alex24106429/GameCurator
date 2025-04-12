package org.alexw.gamecurator.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException; // Import IOException
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Client for interacting with the RAWG API, handling caching via CacheManager.
 */
public class APIClient {

    // --- Configuration ---
    // API_KEY removed, will be fetched from SettingsManager
    private static final int GLOBAL_PAGE_SIZE = 100; // Default page size for API requests

    // --- HTTP Client ---
    // Create a single reusable HttpClient instance
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2) // Use HTTP/2 if available
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // --- API Methods ---

    /**
     * Fetches the top games from the RAWG API, using cache if available.
     * @return A CompletableFuture containing the JSON string of the results array,
     *         or completes exceptionally if the API key is missing.
     */
    public static CompletableFuture<String> getTopGames() {
        String apiKey = SettingsManager.getRawgApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("RAWG API Key is missing. Please configure it in the settings.");
            return CompletableFuture.failedFuture(new IOException("RAWG API Key is not configured."));
        }

        final String cacheKey = "topGames";
        String cachedData = CacheManager.get(cacheKey); // Use CacheManager
        if (cachedData != null) {
            return CompletableFuture.completedFuture(cachedData); // Return cached data immediately
        }

        // Not in cache or expired, fetch from API
        String url = String.format("https://api.rawg.io/api/games?key=%s&page_size=%d", apiKey, GLOBAL_PAGE_SIZE);
        return fetchAndCache(url, cacheKey);
    }

    /**
     * Fetches new games from the RAWG API, using cache if available.
     * @return A CompletableFuture containing the JSON string of the results array,
     *         or completes exceptionally if the API key is missing.
     */
    public static CompletableFuture<String> getNewGames() {
        String apiKey = SettingsManager.getRawgApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("RAWG API Key is missing. Please configure it in the settings.");
            return CompletableFuture.failedFuture(new IOException("RAWG API Key is not configured."));
        }

        final String cacheKey = "newGames";
        String cachedData = CacheManager.get(cacheKey); // Use CacheManager
        if (cachedData != null) {
            return CompletableFuture.completedFuture(cachedData);
        }

        String url = String.format("https://api.rawg.io/api/games?key=%s&page_size=%d&ordering=-released&metacritic=60,100", apiKey, GLOBAL_PAGE_SIZE);
        return fetchAndCache(url, cacheKey);
    }

    /**
     * Searches for games based on a query, using cache if available.
     * @param searchQuery The search term.
     * @return A CompletableFuture containing the JSON string of the results array,
     *         or an empty array "[]" if the query is empty,
     *         or completes exceptionally if the API key is missing or an encoding error occurs.
     */
    public static CompletableFuture<String> searchGames(String searchQuery) {
        String apiKey = SettingsManager.getRawgApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("RAWG API Key is missing. Please configure it in the settings.");
            return CompletableFuture.failedFuture(new IOException("RAWG API Key is not configured."));
        }

        // Sanitize and validate input
        String sanitizedQuery = (searchQuery == null) ? "" : searchQuery.trim().toLowerCase();
        if (sanitizedQuery.isEmpty()) {
            return CompletableFuture.completedFuture("[]"); // Return empty array for empty query
        }

        // Create cache key with normalized query
        String cacheKey = "search_" + sanitizedQuery.replaceAll("\\s+", "_");
        String cachedData = CacheManager.get(cacheKey); // Use CacheManager
        if (cachedData != null) {
            return CompletableFuture.completedFuture(cachedData);
        }

        try {
            String encodedQuery = URLEncoder.encode(sanitizedQuery, StandardCharsets.UTF_8);
            String url = String.format("https://api.rawg.io/api/games?key=%s&page_size=%d&search=%s", apiKey, GLOBAL_PAGE_SIZE, encodedQuery);
            return fetchAndCache(url, cacheKey);
        } catch (Exception e) {
            // Error during URL encoding (unlikely for UTF-8)
            System.err.println("Error encoding search query: " + e.getMessage());
            // Return a failed future for consistency with API key errors
            return CompletableFuture.failedFuture(new IOException("Error encoding search query: " + e.getMessage(), e));
        }
    }

    // --- Helper Method for Fetching and Caching ---

    /**
     * Performs the actual HTTP GET request, parses the "results" field, and caches it using CacheManager.
     * @param url The URL to fetch.
     * @param cacheKey The key to use for caching the result (passed to CacheManager).
     * @return A CompletableFuture containing the JSON string of the results array, or "[]" on error.
     */
    private static CompletableFuture<String> fetchAndCache(String url, String cacheKey) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET() // Default method, explicitly stated for clarity
                .header("Accept", "application/json") // Good practice to specify accept header
                .build();

        System.out.println("Cache miss for: " + cacheKey + ". Fetching from API: " + url);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        String jsonBody = response.body();
                        try {
                            // Use JsonParser to find the "results" array without mapping the whole structure
                            JsonObject parsedJson = JsonParser.parseString(jsonBody).getAsJsonObject();
                            if (parsedJson.has("results") && parsedJson.get("results").isJsonArray()) {
                                String resultsJson = parsedJson.get("results").toString(); // Get the "results" array as a JSON string
                                CacheManager.put(cacheKey, resultsJson); // Cache using CacheManager
                                return resultsJson;
                            } else {
                                System.err.println("API response for key '" + cacheKey + "' missing 'results' array. URL: " + url);
                                return "[]"; // Return empty JSON array string
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to parse JSON response for key '" + cacheKey + "'. Error: " + e.getMessage() + ". URL: " + url);
                            // System.err.println("Response Body: " + jsonBody);
                            return "[]";
                        }
                    } else {
                        // Handle non-successful HTTP status codes
                        System.err.println("API Error for key '" + cacheKey + "'. Status: " + response.statusCode() + ". Body: " + response.body() + ". URL: " + url);
                        return "[]";
                    }
                })
                .exceptionally(e -> {
                    // Handle exceptions during the HTTP request (e.g., network issues)
                    System.err.println("Error fetching data for key '" + cacheKey + "'. Error: " + e.getMessage() + ". URL: " + url);
                    e.printStackTrace(); // Print stack trace for detailed debugging
                    return "[]";
                });
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private APIClient() {
        throw new IllegalStateException("Utility class");
    }
}