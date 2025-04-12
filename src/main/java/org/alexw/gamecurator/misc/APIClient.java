package org.alexw.gamecurator.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
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
    private static final int GLOBAL_PAGE_SIZE = 100; // Default page size for API requests

    // --- HTTP Client ---
    // Create a single reusable HttpClient instance
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2) // Use HTTP/2 if available
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // --- API Methods ---

    // Fetches the top games from the RAWG API, using cache if available.
    public static CompletableFuture<String> getTopGames() {
        String apiKey = SettingsManager.getRawgApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("RAWG API Key is missing. Please configure it in the settings.");
            return CompletableFuture.failedFuture(new IOException("RAWG API Key is not configured."));
        }

        final String cacheKey = "topGames";
        String cachedData = CacheManager.get(cacheKey);
        if (cachedData != null) {
            return CompletableFuture.completedFuture(cachedData);
        }

        // Not in cache or expired, fetch from API
        String url = String.format("https://api.rawg.io/api/games?key=%s&page_size=%d", apiKey, GLOBAL_PAGE_SIZE);
        return fetchAndCache(url, cacheKey);
    }

    // Fetches new games from the RAWG API, using cache if available.
    public static CompletableFuture<String> getNewGames() {
        String apiKey = SettingsManager.getRawgApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("RAWG API Key is missing. Please configure it in the settings.");
            return CompletableFuture.failedFuture(new IOException("RAWG API Key is not configured."));
        }

        final String cacheKey = "newGames";
        String cachedData = CacheManager.get(cacheKey);
        if (cachedData != null) {
            return CompletableFuture.completedFuture(cachedData);
        }

        String url = String.format("https://api.rawg.io/api/games?key=%s&page_size=%d&ordering=-released&metacritic=60,100", apiKey, GLOBAL_PAGE_SIZE);
        return fetchAndCache(url, cacheKey);
    }

    // Searches for games based on a query, using cache if available.
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
        String cachedData = CacheManager.get(cacheKey);
        if (cachedData != null) {
            return CompletableFuture.completedFuture(cachedData);
        }

        String encodedQuery = URLEncoder.encode(sanitizedQuery, StandardCharsets.UTF_8);
		String url = String.format("https://api.rawg.io/api/games?key=%s&page_size=%d&search=%s", apiKey, GLOBAL_PAGE_SIZE, encodedQuery);
		return fetchAndCache(url, cacheKey);
    }

    // --- Helper Method for Fetching and Caching ---
    private static CompletableFuture<String> fetchAndCache(String url, String cacheKey) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
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
                                CacheManager.put(cacheKey, resultsJson);
                                return resultsJson;
                            } else {
                                System.err.println("API response for key '" + cacheKey + "' missing 'results' array. URL: " + url);
                                return "[]";
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to parse JSON response for key '" + cacheKey + "'. Error: " + e.getMessage() + ". URL: " + url);
                            return "[]";
                        }
                    } else {
                        // Handle non-successful HTTP status codes
                        System.err.println("API Error for key '" + cacheKey + "'. Status: " + response.statusCode() + ". Body: " + response.body() + ". URL: " + url);
                        return "[]";
                    }
                })
                .exceptionally(e -> {
                    System.err.println("Error fetching data for key '" + cacheKey + "'. Error: " + e.getMessage() + ". URL: " + url);
                    e.printStackTrace();
                    return "[]";
                });
    }
}