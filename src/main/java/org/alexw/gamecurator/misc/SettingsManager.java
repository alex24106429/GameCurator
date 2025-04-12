package org.alexw.gamecurator.misc;

import java.util.prefs.Preferences;

/**
 * Manages application settings using Java Preferences API.
 */
public class SettingsManager {

    // Preference keys
    private static final String PREF_NODE_PATH = "org/alexw/gamecurator";
    private static final String LLM_API_KEY = "llmApiKey";
    private static final String RAWG_API_KEY = "rawgApiKey";

    private static Preferences getPreferences() {
        // User-specific preferences for this application node
        return Preferences.userRoot().node(PREF_NODE_PATH);
    }

    // --- LLM API Key ---

    public static String getLlmApiKey() {
        return getPreferences().get(LLM_API_KEY, ""); // Return empty string if not set
    }

    public static void setLlmApiKey(String apiKey) {
        if (apiKey != null) {
            getPreferences().put(LLM_API_KEY, apiKey);
        } else {
            getPreferences().remove(LLM_API_KEY); // Remove if null
        }
        // Consider flushing preferences if immediate persistence is critical,
        // but it's usually handled automatically on JVM exit.
        // try { getPreferences().flush(); } catch (BackingStoreException e) { e.printStackTrace(); }
    }

    // --- RAWG API Key ---

    public static String getRawgApiKey() {
        return getPreferences().get(RAWG_API_KEY, ""); // Return empty string if not set
    }

    public static void setRawgApiKey(String apiKey) {
        if (apiKey != null) {
            getPreferences().put(RAWG_API_KEY, apiKey);
        } else {
            getPreferences().remove(RAWG_API_KEY); // Remove if null
        }
        // try { getPreferences().flush(); } catch (BackingStoreException e) { e.printStackTrace(); }
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private SettingsManager() {
        throw new IllegalStateException("Utility class");
    }
}