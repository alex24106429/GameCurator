package org.alexw.gamecurator.misc;

import java.util.prefs.Preferences;

public class SettingsManager {

    private static final String PREF_NODE_PATH = "org/alexw/gamecurator";
    private static final String LLM_API_KEY = "llmApiKey";
    private static final String RAWG_API_KEY = "rawgApiKey";

    private static Preferences getPreferences() {

        return Preferences.userRoot().node(PREF_NODE_PATH);
    }

    public static String getLlmApiKey() {
        return getPreferences().get(LLM_API_KEY, ""); 
    }

    public static void setLlmApiKey(String apiKey) {
        if (apiKey != null) {
            getPreferences().put(LLM_API_KEY, apiKey);
        } else {
            getPreferences().remove(LLM_API_KEY); 
        }
    }

    public static String getRawgApiKey() {
        return getPreferences().get(RAWG_API_KEY, ""); 
    }

    public static void setRawgApiKey(String apiKey) {
        if (apiKey != null) {
            getPreferences().put(RAWG_API_KEY, apiKey);
        } else {
            getPreferences().remove(RAWG_API_KEY); 
        }
    }
}