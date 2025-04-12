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

public class CacheManager {

    private static final long CACHE_TTL = TimeUnit.HOURS.toMillis(24);
    private static final String CACHE_PREFIX = "cache_"; 
    private static final String CACHE_DIR_NAME = ".gamecurator/cache";
    private static final String CACHE_FILE_NAME = "app_cache.json";
    private static final Path CACHE_FILE_PATH;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, CacheEntry> cache;

    static {
        Path cacheDir = Paths.get(System.getProperty("user.home"), CACHE_DIR_NAME);
        CACHE_FILE_PATH = cacheDir.resolve(CACHE_FILE_NAME);
        cache = loadCacheFromFile(); 
        System.out.println("Cache loaded from: " + CACHE_FILE_PATH);
        System.out.println("Initial cache size: " + cache.size());
    }

    private static class CacheEntry {
        final String jsonData; 
        final long creationTime;

        CacheEntry(String jsonData, long creationTime) {
            this.jsonData = jsonData;
            this.creationTime = creationTime;
        }
    }

    public static String get(String item) {
        String key = CACHE_PREFIX + item;
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            return null;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - entry.creationTime > CACHE_TTL) {

            if (cache.remove(key) != null) {
                 saveCacheToFile(); 
            }
            return null;
        }

        return entry.jsonData;
    }

    public static void put(String item, String jsonData) {
        String key = CACHE_PREFIX + item;
        CacheEntry entry = new CacheEntry(jsonData, System.currentTimeMillis());
        cache.put(key, entry);
        saveCacheToFile();
    }

    public static boolean remove(String item) {
        String key = CACHE_PREFIX + item;
        CacheEntry removedEntry = cache.remove(key);
        boolean wasRemoved = removedEntry != null;
        if (wasRemoved) {
            saveCacheToFile();
        }
        return wasRemoved;
    }

    public static void clear() {
        boolean changed = false;

        for (String key : cache.keySet()) {
            if (key.startsWith(CACHE_PREFIX)) {
                cache.remove(key);
                changed = true;
            }
        }

        if (changed) {
            System.out.println("Cache CLEARED.");
            saveCacheToFile();
        } else {
            System.out.println("Cache already empty or no matching prefix found.");
        }
    }

    public static long getCount() {

        return cache.keySet().stream().filter(key -> key.startsWith(CACHE_PREFIX)).count();
    }

    private static Map<String, CacheEntry> loadCacheFromFile() {
        if (!Files.exists(CACHE_FILE_PATH)) {
            System.out.println("Cache file not found (" + CACHE_FILE_PATH + "). Starting with empty cache.");
            return new ConcurrentHashMap<>();
        }

        try (Reader reader = Files.newBufferedReader(CACHE_FILE_PATH, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<ConcurrentHashMap<String, CacheEntry>>() {}.getType();
            Map<String, CacheEntry> loadedCache = gson.fromJson(reader, type);

            return (loadedCache != null) ? new ConcurrentHashMap<>(loadedCache) : new ConcurrentHashMap<>();
        } catch (IOException e) {
            System.err.println("Error reading cache file: " + CACHE_FILE_PATH);
            e.printStackTrace();
            return new ConcurrentHashMap<>(); 
        } catch (JsonSyntaxException e) {
            System.err.println("Error parsing cache file (invalid JSON): " + CACHE_FILE_PATH);
            e.printStackTrace();
            return new ConcurrentHashMap<>(); 
        }
    }

    private static synchronized void saveCacheToFile() {
        try {
            Files.createDirectories(CACHE_FILE_PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(CACHE_FILE_PATH, StandardCharsets.UTF_8)) {
                gson.toJson(cache, writer);
            }
        } catch (IOException e) {
            System.err.println("Error writing cache file: " + CACHE_FILE_PATH);
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Unexpected error saving cache: " + e.getMessage());
            e.printStackTrace();
        }
    }
}