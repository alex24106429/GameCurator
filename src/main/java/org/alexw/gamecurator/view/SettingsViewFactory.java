package org.alexw.gamecurator.view;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.alexw.gamecurator.LibraryManager;
import org.alexw.gamecurator.MainController; // Needed to call switchPage
import org.alexw.gamecurator.misc.CacheManager; // Assuming location
import org.alexw.gamecurator.misc.SettingsManager; // Import SettingsManager
import org.alexw.gamecurator.util.DialogUtils;
import org.alexw.gamecurator.util.IconFactory;

import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class SettingsViewFactory {

    private final Preferences prefs; // Still needed for AI toggle? Or move that to SettingsManager too? Keep for now.
    private final LibraryManager libraryManager; // Needed for reset
    private final MainController mainController; // Needed for page refresh after reset

    // Preference Keys (centralize if used elsewhere)
    private static final String PREF_AI_RECOMMENDATIONS = "aiRecommendationsEnabled";

    // TextFields for API Keys to access them in the save handler
    private TextField llmApiKeyField;
    private TextField rawgApiKeyField;

    public SettingsViewFactory(Preferences prefs, LibraryManager libraryManager, MainController mainController) {
        this.prefs = prefs;
        this.libraryManager = libraryManager;
        this.mainController = mainController;
    }

    public Parent createSettingsView() {
        VBox settingsPane = new VBox(15);
        settingsPane.setPadding(new Insets(20));
        settingsPane.getStyleClass().add("settings-pane");

        // --- API Keys Section ---
        settingsPane.getChildren().add(createSettingHeader("API Keys"));
        settingsPane.getChildren().add(createApiKeysSection());

        // --- AI Features Section ---
        settingsPane.getChildren().add(createSettingHeader("AI Features"));
        CheckBox aiRecommendCheckBox = new CheckBox();
        aiRecommendCheckBox.setSelected(prefs.getBoolean(PREF_AI_RECOMMENDATIONS, true)); // Default true
        aiRecommendCheckBox.setOnAction(e -> {
            prefs.putBoolean(PREF_AI_RECOMMENDATIONS, aiRecommendCheckBox.isSelected());
            System.out.println("AI Recommendations setting changed to: " + aiRecommendCheckBox.isSelected());
            // Refresh assistant page if visible to update button state immediately
            mainController.refreshCurrentPageIf("assistant");
        });
        settingsPane.getChildren().add(createSettingArea("AUTO_FIX", aiRecommendCheckBox, "Enable Recommendations", "Allow AI to generate game recommendations based on your library. Requires cached game data and a configured LLM API Key."));

        // --- Data Section ---
        settingsPane.getChildren().add(createSettingHeader("Data Management"));
        Button clearCacheButton = new Button("Clear Cache");
        clearCacheButton.setOnAction(this::handleClearCache);
        settingsPane.getChildren().add(createSettingArea("DELETE", clearCacheButton, "Clear Cache", "Delete cached API data, images, and AI recommendations. Your library list and settings (including API keys) are kept."));

        Button resetButton = new Button("Reset App");
        resetButton.setStyle("-fx-text-fill: red;"); // Style for danger
        resetButton.setOnAction(this::handleResetApp);
        settingsPane.getChildren().add(createSettingArea("RESTORE", resetButton, "Reset Application", "Delete ALL cached data and reset ALL settings (including API keys and your game library). This action cannot be undone."));

        // --- About Section ---
        settingsPane.getChildren().add(createSettingHeader("About"));
        // Consider loading version from a properties file or MANIFEST.MF
        settingsPane.getChildren().add(new Label("GameCurator v1.1.0"));

        ScrollPane scrollPane = new ScrollPane(settingsPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // Avoid horizontal scrollbar
        return scrollPane;
    }

    // Helper to create the API Keys input section
    private Node createApiKeysSection() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(5, 0, 15, 0)); // Padding below section

        Label llmKeyLabel = new Label("Gemini API Key:");
        llmApiKeyField = new TextField(SettingsManager.getLlmApiKey()); // Load initial value
        llmApiKeyField.setPromptText("Enter your Gemini API Key");
        GridPane.setHgrow(llmApiKeyField, Priority.ALWAYS);

        Label rawgKeyLabel = new Label("RAWG API Key:");
        rawgApiKeyField = new TextField(SettingsManager.getRawgApiKey()); // Load initial value
        rawgApiKeyField.setPromptText("Enter your RAWG API Key");
        GridPane.setHgrow(rawgApiKeyField, Priority.ALWAYS);

        Button saveApiKeysButton = new Button("Save API Keys");
        saveApiKeysButton.setOnAction(this::handleSaveApiKeys);

        // Layout in grid
        grid.add(llmKeyLabel, 0, 0);
        grid.add(llmApiKeyField, 1, 0);
        grid.add(rawgKeyLabel, 0, 1);
        grid.add(rawgApiKeyField, 1, 1);

        // Add save button below, spanning columns or aligned right
        HBox buttonBox = new HBox(saveApiKeysButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0)); // Top padding for button
        grid.add(buttonBox, 0, 2, 2, 1); // Span 2 columns

        return grid;
    }

    // Action handler for saving API keys
    private void handleSaveApiKeys(ActionEvent event) {
        String llmKey = llmApiKeyField.getText();
        String rawgKey = rawgApiKeyField.getText();

        SettingsManager.setLlmApiKey(llmKey);
        SettingsManager.setRawgApiKey(rawgKey);

        System.out.println("API Keys saved.");
        DialogUtils.showInfoDialog("API Keys Saved", "Your API keys have been saved successfully.");

        // Optionally, refresh other views if they depend on the keys being present
        // e.g., re-enable buttons or trigger checks
        mainController.refreshCurrentPageIf("assistant"); // Refresh assistant if it depends on LLM key
    }


    // Helper to create a consistent setting row layout
    private Node createSettingArea(String iconIdentifier, Node control, String controlLabelText, String description) {
        HBox area = new HBox(15);
        area.setPadding(new Insets(5, 0, 5, 0));
        area.setAlignment(Pos.CENTER_LEFT);
        area.getStyleClass().add("setting-area");

        Node icon = IconFactory.createIcon(iconIdentifier, IconFactory.SETTINGS_ICON_SIZE);
        HBox iconContainer = new HBox(icon);
        iconContainer.setMinWidth(30); // Give icon some space
        iconContainer.setAlignment(Pos.CENTER);

        VBox descriptionBox = new VBox(2);
        Label controlLabel = new Label(controlLabelText);
        controlLabel.setStyle("-fx-font-weight: bold;");

        Label descLabel = new Label(description);
        descLabel.setWrapText(true);
        // descLabel.setMaxWidth(350); // Let VBox/HBox manage width
        VBox.setVgrow(descLabel, Priority.ALWAYS);
        descriptionBox.getChildren().addAll(controlLabel, descLabel);
        HBox.setHgrow(descriptionBox, Priority.ALWAYS); // Let description take available space

        Region spacer = new Region();
        // HBox.setHgrow(spacer, Priority.ALWAYS); // Use Hgrow on descriptionBox instead

        HBox controlContainer = new HBox(control);
        controlContainer.setAlignment(Pos.CENTER_RIGHT);
        controlContainer.setMinWidth(Region.USE_PREF_SIZE); // Ensure control doesn't get squashed

        area.getChildren().addAll(iconContainer, descriptionBox, /*spacer,*/ controlContainer);
        return area;
    }

    // Helper for section headers
    private Label createSettingHeader(String text) {
        Label header = new Label(text);
        header.getStyleClass().add("settings-header"); // Use CSS for styling
        header.setMaxWidth(Double.MAX_VALUE); // Ensure it spans width
        // Add padding/margin via CSS or here
        header.setPadding(new Insets(10, 0, 5, 0));
        return header;
    }

    // Action handler for clearing cache
    private void handleClearCache(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Clear Cache");
        alert.setHeaderText("Are you sure you want to clear the cache?");
        alert.setContentText("This will remove temporary data (API responses, images, AI results) but keep your library list and settings (including API keys).");
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL); // Explicit buttons

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            System.out.println("Clearing cache...");
            CacheManager.clear();
            DialogUtils.showInfoDialog("Cache Cleared", "Application cache has been cleared.");
            // Refresh assistant page if visible to clear any displayed results
             mainController.refreshCurrentPageIf("assistant");
        }
    }

    // Action handler for resetting the application
    private void handleResetApp(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reset Application");
        alert.setHeaderText("WARNING: This will delete ALL data!");
        alert.setContentText("Are you sure you want to reset the application? This includes your library, settings (including API keys), and all cached data. This action cannot be undone.");
        // Make YES the more dangerous-looking button if possible, or just be clear
        ButtonType yesButton = new ButtonType("Yes, Reset Everything", ButtonBar.ButtonData.YES);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(yesButton, cancelButton);


        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == yesButton) {
            System.out.println("Resetting application...");
            try {
                // Clear preferences first (includes API keys managed by SettingsManager now)
                SettingsManager.setLlmApiKey(null); // Explicitly clear keys via manager
                SettingsManager.setRawgApiKey(null);
                prefs.clear(); // Clear other prefs like AI toggle
                prefs.flush(); // Ensure changes are written

                // Clear library data managed by LibraryManager
                libraryManager.clearLibrary();

                // Clear file-based cache
                CacheManager.clear(); // Clear all cached files/data

                // Use the MainController reference to switch page and show dialog AFTER reset logic
                Platform.runLater(() -> {
                    // Re-initialize or refresh necessary parts of MainController if needed
                    // For now, just switch page which reloads settings view with defaults
                    mainController.switchPage("settings"); // This should reload the view with empty fields
                    DialogUtils.showInfoDialog("Application Reset", "All data has been cleared. Settings reset to default.");
                    // Also refresh navigation bar in case defaults changed something visual? Unlikely here.
                    // mainController.setupNavigationBar(); // If needed
                    // Refresh other pages too?
                    mainController.refreshCurrentPageIf("library");
                    mainController.refreshCurrentPageIf("assistant");
                });
            } catch (BackingStoreException e) {
                System.err.println("Error clearing preferences during reset: " + e.getMessage());
                DialogUtils.showErrorDialog("Reset Error", "Could not clear all settings: " + e.getMessage());
            } catch (Exception e) {
                // Catch other potential errors during reset
                System.err.println("Error during application reset: " + e.getMessage());
                e.printStackTrace();
                 DialogUtils.showErrorDialog("Reset Error", "An unexpected error occurred during reset: " + e.getMessage());
            }
        }
    }
}
