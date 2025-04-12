package org.alexw.gamecurator.view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.alexw.gamecurator.LibraryManager;
import org.alexw.gamecurator.ai.LLMClient; // Assuming LLMClient location
import org.alexw.gamecurator.util.DialogUtils;
import org.alexw.gamecurator.util.IconFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

public class AssistantViewFactory {

    private final LibraryManager libraryManager;
    private final Preferences prefs;
    private static final String PREF_AI_RECOMMENDATIONS = "aiRecommendationsEnabled"; // Keep pref key accessible


    public AssistantViewFactory(LibraryManager libraryManager, Preferences prefs) {
        this.libraryManager = libraryManager;
        this.prefs = prefs;
    }

    public Parent createAssistantView() {
        VBox assistantPane = new VBox(15);
        assistantPane.setPadding(new Insets(20));
        assistantPane.getStyleClass().add("assistant-pane");

        // --- Controls ---
        Button getRecsButton = new Button("Get Recommendations");
        getRecsButton.setGraphic(IconFactory.createIcon("REFRESH", IconFactory.BUTTON_ICON_SIZE)); // Or "ROBOT"
        // Initial state check
        boolean aiEnabled = prefs.getBoolean(PREF_AI_RECOMMENDATIONS, true);
        boolean libraryEmpty = libraryManager.getLibraryItemIds().isEmpty();
        getRecsButton.setDisable(!aiEnabled || libraryEmpty);


        ProgressIndicator loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(30, 30);
        loadingIndicator.setVisible(false); // Initially hidden

        HBox buttonArea = new HBox(10, getRecsButton, loadingIndicator);
        buttonArea.setAlignment(Pos.CENTER_LEFT);

        // --- Results Display Area ---
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        VBox resultsContainer = new VBox(10); // Container for results text/list
        resultsContainer.setPadding(new Insets(10));
        resultsContainer.getStyleClass().add("results-container"); // Add class for potential lookup/styling
        scrollPane.setContent(resultsContainer);
        VBox.setVgrow(scrollPane, Priority.ALWAYS); // Make scroll pane expand

        // --- Initial Info / Status Label ---
        Label statusLabel = new Label();
        updateStatusLabel(statusLabel, aiEnabled, libraryEmpty); // Set initial text
        resultsContainer.getChildren().add(statusLabel);


        // --- Button Action ---
        getRecsButton.setOnAction(event -> {
            // Re-check conditions before proceeding
            boolean currentAiEnabled = prefs.getBoolean(PREF_AI_RECOMMENDATIONS, true);
            Set<Integer> libraryIds = libraryManager.getLibraryItemIds();
            boolean currentLibraryEmpty = libraryIds.isEmpty();

            if (currentLibraryEmpty) {
                DialogUtils.showInfoDialog("Empty Library", "Please add games to your library before getting recommendations.");
                updateStatusLabel(statusLabel, currentAiEnabled, currentLibraryEmpty); // Update status if needed
                getRecsButton.setDisable(true);
                return;
            }
            if (!currentAiEnabled) {
                DialogUtils.showInfoDialog("AI Disabled", "AI Recommendations are disabled in Settings.");
                 updateStatusLabel(statusLabel, currentAiEnabled, currentLibraryEmpty); // Update status if needed
                 getRecsButton.setDisable(true);
                return;
            }

            // Start loading state
            getRecsButton.setDisable(true);
            loadingIndicator.setVisible(true);
            resultsContainer.getChildren().clear(); // Clear previous results/status
            Label generatingLabel = new Label("Generating recommendations... (This may take a moment)");
            resultsContainer.getChildren().add(generatingLabel);

            // Call the AI Client asynchronously
            CompletableFuture<LLMClient.GameRecommendations> recommendationFuture =
                    LLMClient.getGameRecommendations(libraryIds);

            recommendationFuture.whenCompleteAsync((recommendations, error) -> {
                // This block runs on the JavaFX Application Thread
                // Re-enable button based on current state, not just completion
                boolean latestAiEnabled = prefs.getBoolean(PREF_AI_RECOMMENDATIONS, true);
                boolean latestLibraryEmpty = libraryManager.getLibraryItemIds().isEmpty();
                getRecsButton.setDisable(!latestAiEnabled || latestLibraryEmpty);

                loadingIndicator.setVisible(false);
                resultsContainer.getChildren().remove(generatingLabel); // Remove "Generating..." message

                if (error != null) {
                    // Handle errors (API, network, parsing, etc.)
                    System.err.println("Error fetching recommendations: " + error.getMessage());
                    error.printStackTrace();
                    Label errorHeader = new Label("Error Getting Recommendations:");
                    errorHeader.setTextFill(Color.RED);
                    errorHeader.setStyle("-fx-font-weight: bold;");
                    Label errorLabel = new Label(error.getMessage());
                    errorLabel.setTextFill(Color.RED);
                    errorLabel.setWrapText(true);
                    resultsContainer.getChildren().addAll(errorHeader, errorLabel);

                    // Display cause if available
                    Throwable cause = error.getCause();
                    if(cause != null) {
                        Label causeLabel = new Label("Details: " + cause.getMessage());
                        causeLabel.setWrapText(true);
                         causeLabel.setTextFill(Color.DARKRED); // Make it look like part of the error
                        resultsContainer.getChildren().add(causeLabel);
                    }
                     // Add the status label back at the bottom in case of error
                    updateStatusLabel(statusLabel, latestAiEnabled, latestLibraryEmpty);
                    resultsContainer.getChildren().add(statusLabel);

                } else if (recommendations != null) {
                    // Display successful recommendations
                    displayRecommendations(recommendations, resultsContainer);
                     // Add the status label back at the bottom after results
                    updateStatusLabel(statusLabel, latestAiEnabled, latestLibraryEmpty);
                    resultsContainer.getChildren().add(statusLabel);
                } else {
                    // Should not happen if error is null, but handle defensively
                    resultsContainer.getChildren().add(new Label("Received null recommendations without an error."));
                     // Add the status label back
                    updateStatusLabel(statusLabel, latestAiEnabled, latestLibraryEmpty);
                    resultsContainer.getChildren().add(statusLabel);
                }

            }, Platform::runLater); // Ensure completion runs on FX thread
        });

        assistantPane.getChildren().addAll(buttonArea, scrollPane);
        return assistantPane;
    }

     // Helper to update the status label text
    private void updateStatusLabel(Label label, boolean aiEnabled, boolean libraryEmpty) {
        if (!aiEnabled) {
            label.setText("AI Recommendations are disabled in Settings.");
            label.setTextFill(Color.ORANGERED);
        } else if (libraryEmpty) {
            label.setText("Add some games to your library first!");
             label.setTextFill(Color.ORANGE);
        } else {
            label.setText("Click 'Get Recommendations' based on your library.");
            label.setTextFill(Color.BLACK); // Default text color
        }
    }


    // Helper method to display recommendations in the Assistant view
    private void displayRecommendations(LLMClient.GameRecommendations recommendations, VBox container) {
        // container should already be cleared by the caller before calling this

        // Display Reasoning
        if (recommendations.getReasoning() != null && !recommendations.getReasoning().isBlank()) {
            Label reasoningHeader = new Label("AI Reasoning:");
            reasoningHeader.setStyle("-fx-font-weight: bold;");
            Text reasoningText = new Text(recommendations.getReasoning());
            TextFlow reasoningFlow = new TextFlow(reasoningText); // Use TextFlow for wrapping
            reasoningFlow.setPadding(new Insets(0, 0, 10, 0)); // Add some space below reasoning
             container.getChildren().addAll(reasoningHeader, reasoningFlow);
        }


        // Display Recommended Games List
        Label answerHeader = new Label("Recommendations:");
        answerHeader.setStyle("-fx-font-weight: bold;");
        container.getChildren().add(answerHeader);

        if (recommendations.getAnswer() == null || recommendations.getAnswer().isEmpty()) {
            container.getChildren().add(new Label("No specific game titles were recommended."));
        } else {
            ListView<String> recListView = new ListView<>();
            recListView.getItems().addAll(recommendations.getAnswer());
            // Optional: Make list view non-editable, set fixed height or preferred height
            recListView.setPrefHeight(Math.min(recommendations.getAnswer().size() * 28.0 + 5, 300)); // Estimate height + buffer, limit max height
            recListView.setMaxHeight(Region.USE_PREF_SIZE);
            container.getChildren().add(recListView);
        }
    }
}
