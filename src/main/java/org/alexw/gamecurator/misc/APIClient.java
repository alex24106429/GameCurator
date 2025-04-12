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

public class APIClient {

    private static final int GLOBAL_PAGE_SIZE = 100; 

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2) 
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

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

        String url = String.format("https://api.rawg.io/api/games?key=%s&page_size=%d", apiKey, GLOBAL_PAGE_SIZE);
        return fetchAndCache(url, cacheKey);
    }

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

    public static CompletableFuture<String> searchGames(String searchQuery) {
        String apiKey = SettingsManager.getRawgApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("RAWG API Key is missing. Please configure it in the settings.");
            return CompletableFuture.failedFuture(new IOException("RAWG API Key is not configured."));
        }

        String sanitizedQuery = (searchQuery == null) ? "" : searchQuery.trim().toLowerCase();
        if (sanitizedQuery.isEmpty()) {
            return CompletableFuture.completedFuture("[]"); 
        }

        String cacheKey = "search_" + sanitizedQuery.replaceAll("\\s+", "_");
        String cachedData = CacheManager.get(cacheKey);
        if (cachedData != null) {
            return CompletableFuture.completedFuture(cachedData);
        }

        String encodedQuery = URLEncoder.encode(sanitizedQuery, StandardCharsets.UTF_8);
		String url = String.format("https://api.rawg.io/api/games?key=%s&page_size=%d&search=%s", apiKey, GLOBAL_PAGE_SIZE, encodedQuery);
		return fetchAndCache(url, cacheKey);
    }

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

                            JsonObject parsedJson = JsonParser.parseString(jsonBody).getAsJsonObject();
                            if (parsedJson.has("results") && parsedJson.get("results").isJsonArray()) {
                                String resultsJson = parsedJson.get("results").toString(); 
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