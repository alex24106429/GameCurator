package org.alexw.gamecurator.misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages a persistent cache with Time-To-Live (TTL) functionality.
 * Cache data is stored in a JSON file in the user's home directory.
 * This class provides static methods for thread-safe cache operations.
 */
public class CacheManager {

    // --- Configuration ---
    private static final long CACHE_TTL = TimeUnit.HOURS.toMillis(24); // 24 hours in milliseconds
    private static final String CACHE_PREFIX = "cache_"; // Prefix for all cache keys
    private static final String CACHE_DIR_NAME = ".gamecurator/cache";
    private static final String CACHE_FILE_NAME = "app_cache.json";
    private static final Path CACHE_FILE_PATH;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create(); // For serialization

    // --- In-Memory Cache ---
    // Needs to be non-final to be replaced during loading
    private static Map<String, CacheEntry> cache;

    // Static initializer to determine cache path and load cache from file
    static {
        Path cacheDir = Paths.get(System.getProperty("user.home"), CACHE_DIR_NAME);
        CACHE_FILE_PATH = cacheDir.resolve(CACHE_FILE_NAME);
        cache = loadCacheFromFile(); // Load cache on class initialization
        System.out.println("Cache loaded from: " + CACHE_FILE_PATH);
        System.out.println("Initial cache size: " + cache.size());
    }


    // Using a nested static class for CacheEntry
    private static class CacheEntry {
        final String jsonData; // Store the JSON string directly
        final long creationTime;

        // Constructor needed for Gson deserialization
        CacheEntry(String jsonData, long creationTime) {
            this.jsonData = jsonData;
            this.creationTime = creationTime;
        }
    }

    // --- Cache Management Methods ---

    /**
     * Retrieves an item from the cache if it exists and hasn't expired.
     * Removes expired items upon access.
     * @param item The identifier for the cached item (will be prefixed internally).
     * @return The cached JSON data string, or null if not found or expired.
     */
    public static String get(String item) {
        String key = CACHE_PREFIX + item;
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            // System.out.println("Cache MISS for: " + key);
            return null; // Not found
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - entry.creationTime > CACHE_TTL) {
            // System.out.println("Cache EXPIRED for: " + key);
            // Remove expired entry and save the change
            if (cache.remove(key) != null) {
                 saveCacheToFile(); // Save after removal
            }
            return null; // Expired
        }

        // System.out.println("Cache HIT for: " + key);
        return entry.jsonData; // Found and valid
    }

    /**
     * Writes data to the cache and persists the change to disk.
     * @param item The identifier for the cached item (will be prefixed internally).
     * @param jsonData The JSON data string to store.
     */
    public static void put(String item, String jsonData) {
        String key = CACHE_PREFIX + item;
        CacheEntry entry = new CacheEntry(jsonData, System.currentTimeMillis());
        cache.put(key, entry);
        // System.out.println("Cache WRITE for: " + key);
        saveCacheToFile(); // Save after putting data
    }

    /**
     * Removes a specific item from the cache and persists the change to disk.
     * Does nothing if the item is not found.
     *
     * @param item The identifier for the cached item to remove (will be prefixed internally).
     * @return true if an item was actually removed, false otherwise.
     */
    public static boolean remove(String item) {
        String key = CACHE_PREFIX + item;
        CacheEntry removedEntry = cache.remove(key);
        boolean wasRemoved = removedEntry != null;
        if (wasRemoved) {
            // System.out.println("Cache REMOVE success for: " + key);
            saveCacheToFile(); // Save after removal
        } else {
            // System.out.println("Cache REMOVE attempt for: " + key + " - NOT FOUND");
        }
        return wasRemoved;
    }


    /**
     * Removes all entries from the cache managed by this class (matching the prefix)
     * and persists the change to disk.
     */
    public static void clear() {
        boolean changed = false;
        // Iterate and remove to know if something was actually removed
        for (String key : cache.keySet()) {
            if (key.startsWith(CACHE_PREFIX)) {
                cache.remove(key);
                changed = true;
            }
        }
        // A more efficient way for ConcurrentHashMap, but doesn't easily tell us if changed:
        // cache.keySet().removeIf(key -> key.startsWith(CACHE_PREFIX));

        if (changed) {
            System.out.println("Cache CLEARED.");
            saveCacheToFile(); // Save after clearing
        } else {
             System.out.println("Cache already empty or no matching prefix found.");
        }
    }

    /**
     * Counts the number of items currently in the cache managed by this class.
     * Does not filter out expired items actively, relies on 'get' or explicit removal.
     * @return The count of cached items matching the prefix.
     */
    public static long getCount() {
        // Count keys starting with the prefix using streams
        return cache.keySet().stream().filter(key -> key.startsWith(CACHE_PREFIX)).count();
    }

    // --- Persistence Methods ---

    /**
     * Loads the cache from the JSON file.
     * If the file doesn't exist or an error occurs, returns an empty cache.
     * @return A ConcurrentHashMap representing the loaded cache.
     */
    private static Map<String, CacheEntry> loadCacheFromFile() {
        if (!Files.exists(CACHE_FILE_PATH)) {
            System.out.println("Cache file not found (" + CACHE_FILE_PATH + "). Starting with empty cache.");
            return new ConcurrentHashMap<>();
        }

        try (Reader reader = Files.newBufferedReader(CACHE_FILE_PATH, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<ConcurrentHashMap<String, CacheEntry>>() {}.getType();
            Map<String, CacheEntry> loadedCache = gson.fromJson(reader, type);
            // Return a new ConcurrentHashMap based on the loaded data (or empty if null)
            return (loadedCache != null) ? new ConcurrentHashMap<>(loadedCache) : new ConcurrentHashMap<>();
        } catch (IOException e) {
            System.err.println("Error reading cache file: " + CACHE_FILE_PATH);
            e.printStackTrace(); // Log the error
            return new ConcurrentHashMap<>(); // Return empty cache on I/O error
        } catch (JsonSyntaxException e) {
            System.err.println("Error parsing cache file (invalid JSON): " + CACHE_FILE_PATH);
            e.printStackTrace(); // Log the error
            // Optionally: Backup the corrupted file before returning empty
            return new ConcurrentHashMap<>(); // Return empty cache on JSON error
        }
    }

    /**
     * Saves the current state of the cache map to the JSON file.
     * This method is synchronized to prevent concurrent write issues.
     */
    private static synchronized void saveCacheToFile() {
        try {
            // Ensure the directory exists
            Files.createDirectories(CACHE_FILE_PATH.getParent());

            // Write the cache map to the file
            try (Writer writer = Files.newBufferedWriter(CACHE_FILE_PATH, StandardCharsets.UTF_8)) {
                gson.toJson(cache, writer);
            }
            // System.out.println("Cache saved to: " + CACHE_FILE_PATH);
        } catch (IOException e) {
            System.err.println("Error writing cache file: " + CACHE_FILE_PATH);
            e.printStackTrace(); // Log the error
        } catch (Exception e) {
            System.err.println("Unexpected error saving cache: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private CacheManager() {
        throw new IllegalStateException("Utility class");
    }
}