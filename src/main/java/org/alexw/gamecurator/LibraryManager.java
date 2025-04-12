package org.alexw.gamecurator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.alexw.gamecurator.misc.CacheManager;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class LibraryManager {

    private static final String PREF_LIBRARY = "libraryItems";
    private final Preferences prefs;
    private final Gson gson;

    public LibraryManager(Preferences prefs, Gson gson) {
        this.prefs = prefs;
        this.gson = gson;
    }

    public Set<Integer> getLibraryItemIds() {
        String json = prefs.get(PREF_LIBRARY, "[]");
        try {
            Type setType = new TypeToken<Set<Integer>>() {}.getType();
            Set<Integer> ids = gson.fromJson(json, setType);
            return (ids != null) ? ids : new HashSet<>();
        } catch (Exception e) {
            System.err.println("Error reading library preferences: " + e.getMessage());
            return new HashSet<>();
        }
    }

    public boolean addLibraryItem(int gameId, JsonObject gameData) {
        Set<Integer> currentLibrary = getLibraryItemIds();
        boolean added = currentLibrary.add(gameId);
        if (added) {
            saveLibraryItemIds(currentLibrary);
            // Ensure essential fields (name, genres) are present before caching
            if (gameData == null || !gameData.has("name") || !gameData.has("genres")) {
                System.err.println("Warning: Caching game data for ID " + gameId + " without required fields (name, genres) for AI.");
            }
            // Only cache if gameData is not null
            if (gameData != null) {
                 CacheManager.put("gameData_" + gameId, gameData.toString());
                 System.out.println("Added game " + gameId + " to library and cached data.");
            } else {
                 System.out.println("Added game " + gameId + " to library (no data provided to cache).");
            }
        }
        return added;
    }

    public boolean removeLibraryItem(int gameId) {
        Set<Integer> currentLibrary = getLibraryItemIds();
        boolean removed = currentLibrary.remove(gameId);
        if (removed) {
            saveLibraryItemIds(currentLibrary);
            CacheManager.remove("gameData_" + gameId);
            System.out.println("Removed game " + gameId + " from library and cache.");
        }
        return removed;
    }

    private void saveLibraryItemIds(Set<Integer> libraryItemIds) {
        String json = gson.toJson(libraryItemIds);
        prefs.put(PREF_LIBRARY, json);
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            System.err.println("Error saving library preferences: " + e.getMessage());
        }
    }

    public boolean isInLibrary(int gameId) {
        return getLibraryItemIds().contains(gameId);
    }

    // Used by Settings Reset
    public void clearLibrary() {
        saveLibraryItemIds(new HashSet<>());
    }
}
