package org.alexw.gamecurator.view;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import org.alexw.gamecurator.ai.LLMClient;
import org.alexw.gamecurator.misc.CacheManager;
import org.alexw.gamecurator.util.DialogUtils;
import org.alexw.gamecurator.util.IconFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.prefs.Preferences;

public class AssistantViewFactory implements ViewFactory {

    private final LibraryManager libraryManager;
    private final Preferences prefs;
    private static final String PREF_AI_RECOMMENDATIONS = "aiRecommendationsEnabled";

    public AssistantViewFactory(LibraryManager libraryManager, Preferences prefs) {
        this.libraryManager = libraryManager;
        this.prefs = prefs;
    }

    @Override
    public Parent createView() {
        VBox assistantPane = new VBox(15);
        assistantPane.setPadding(new Insets(20));
        assistantPane.getStyleClass().add("assistant-pane");

        Button getRecsButton = new Button("Get Recommendations");
        getRecsButton.setGraphic(IconFactory.createIcon("REFRESH", IconFactory.BUTTON_ICON_SIZE));

        boolean aiEnabled = prefs.getBoolean(PREF_AI_RECOMMENDATIONS, true);
        boolean libraryEmpty = libraryManager.getLibraryItemIds().isEmpty();
        getRecsButton.setDisable(!aiEnabled || libraryEmpty);

        ProgressIndicator loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(30, 30);
        loadingIndicator.setVisible(false); 

        HBox buttonArea = new HBox(10, getRecsButton, loadingIndicator);
        buttonArea.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        VBox resultsContainer = new VBox(10); 
        resultsContainer.setPadding(new Insets(10));
        resultsContainer.getStyleClass().add("results-container");
        scrollPane.setContent(resultsContainer);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Label statusLabel = new Label();
        updateStatusLabel(statusLabel, aiEnabled, libraryEmpty); 
        resultsContainer.getChildren().add(statusLabel);

        getRecsButton.setOnAction(event -> {

            boolean currentAiEnabled = prefs.getBoolean(PREF_AI_RECOMMENDATIONS, true);
            Set<Integer> libraryIds = libraryManager.getLibraryItemIds();
            boolean currentLibraryEmpty = libraryIds.isEmpty();

            if (currentLibraryEmpty) {
                DialogUtils.showInfoDialog("Empty Library", "Please add games to your library before getting recommendations.");
                updateStatusLabel(statusLabel, currentAiEnabled, currentLibraryEmpty);
                getRecsButton.setDisable(true);
                return;
            }
            if (!currentAiEnabled) {
                DialogUtils.showInfoDialog("AI Disabled", "AI Recommendations are disabled in Settings.");
                 updateStatusLabel(statusLabel, currentAiEnabled, currentLibraryEmpty);
                 getRecsButton.setDisable(true);
                return;
            }

            getRecsButton.setDisable(true);
            loadingIndicator.setVisible(true);
            resultsContainer.getChildren().clear();
            Label generatingLabel = new Label("Generating recommendations... (This may take a moment)");
            resultsContainer.getChildren().add(generatingLabel);

            StringBuilder promptBuilder = new StringBuilder();
            int gamesProcessed = 0;
            for (int gameId : libraryIds) {
                String gameDataJson = CacheManager.get("gameData_" + gameId);
                if (gameDataJson != null) {
                    try {
                        JsonObject game = JsonParser.parseString(gameDataJson).getAsJsonObject();
                        String name = game.has("name") ? game.get("name").getAsString() : "Unknown Title";

                        String genres = "Unknown Genres";
                        if (game.has("genres") && game.get("genres").isJsonArray()) {
                            genres = game.getAsJsonArray("genres").asList().stream()
                                    .filter(JsonElement::isJsonObject)
                                    .map(g -> g.getAsJsonObject().has("name") ? g.getAsJsonObject().get("name").getAsString() : "")
                                    .filter(s -> !s.isEmpty())
                                    .collect(Collectors.joining(" "));
                        }
                        if (genres.isEmpty()) genres = "Unknown Genres";

                        promptBuilder.append("Title: ").append(name).append("\n");
                        promptBuilder.append("Genres: ").append(genres).append("\n---\n");
                        gamesProcessed++;
                    } catch (Exception e) {
                        System.err.println("Error processing cached game data for ID " + gameId + " in AssistantViewFactory: " + e.getMessage());
                    }
                } else {
                    System.err.println("Could not find cached game data for ID " + gameId + " in library (AssistantViewFactory).");
                }
            }

            if (gamesProcessed == 0) {
                System.err.println("Could not build prompt, no valid library game data found in cache.");

                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    resultsContainer.getChildren().remove(generatingLabel);
                    resultsContainer.getChildren().add(new Label("Error: Could not process library data for recommendations."));
                    boolean latestAiEnabled = prefs.getBoolean(PREF_AI_RECOMMENDATIONS, true);
                    boolean latestLibraryEmpty = libraryManager.getLibraryItemIds().isEmpty();
                    getRecsButton.setDisable(!latestAiEnabled || latestLibraryEmpty);
                    updateStatusLabel(statusLabel, latestAiEnabled, latestLibraryEmpty);
                    resultsContainer.getChildren().add(statusLabel);
                });
                return; 
            }
            String userPrompt = promptBuilder.toString();

            CompletableFuture<LLMClient.GameRecommendations> recommendationFuture =
                    LLMClient.getGameRecommendations(userPrompt);

            recommendationFuture.whenCompleteAsync((recommendations, error) -> {

                boolean latestAiEnabled = prefs.getBoolean(PREF_AI_RECOMMENDATIONS, true);
                boolean latestLibraryEmpty = libraryManager.getLibraryItemIds().isEmpty();
                getRecsButton.setDisable(!latestAiEnabled || latestLibraryEmpty);

                loadingIndicator.setVisible(false);
                resultsContainer.getChildren().remove(generatingLabel);

                if (error != null) {
                    resultsContainer.getChildren().add(new Label("Received error:" + error));
                } else if (recommendations != null) {

                    displayRecommendations(recommendations, resultsContainer);
                    updateStatusLabel(statusLabel, latestAiEnabled, latestLibraryEmpty);
                    resultsContainer.getChildren().add(statusLabel);
                } else {
                    resultsContainer.getChildren().add(new Label("Received null recommendations without an error."));
                    updateStatusLabel(statusLabel, latestAiEnabled, latestLibraryEmpty);
                    resultsContainer.getChildren().add(statusLabel);
                }

            }, Platform::runLater);
        });

        assistantPane.getChildren().addAll(buttonArea, scrollPane);
        return assistantPane;
    }

    private void updateStatusLabel(Label label, boolean aiEnabled, boolean libraryEmpty) {
        if (!aiEnabled) {
            label.setText("AI Recommendations are disabled in Settings.");
            label.setTextFill(Color.ORANGERED);
        } else if (libraryEmpty) {
            label.setText("Add some games to your library first!");
             label.setTextFill(Color.ORANGE);
        } else {
            label.setText("Click 'Get Recommendations' based on your library.");
            label.setTextFill(Color.BLACK);
        }
    }

    private void displayRecommendations(LLMClient.GameRecommendations recommendations, VBox container) {

        if (recommendations.getReasoning() != null && !recommendations.getReasoning().isBlank()) {
            Label reasoningHeader = new Label("AI Reasoning:");
            reasoningHeader.setStyle("-fx-font-weight: bold;");
            Text reasoningText = new Text(recommendations.getReasoning());
            TextFlow reasoningFlow = new TextFlow(reasoningText);
            reasoningFlow.setPadding(new Insets(0, 0, 10, 0));
            container.getChildren().addAll(reasoningHeader, reasoningFlow);
        }

        Label answerHeader = new Label("Recommendations:");
        answerHeader.setStyle("-fx-font-weight: bold;");
        container.getChildren().add(answerHeader);

        if (recommendations.getAnswer() == null || recommendations.getAnswer().isEmpty()) {
            container.getChildren().add(new Label("No specific game titles were recommended."));
        } else {
            ListView<String> recListView = new ListView<>();
            recListView.getItems().addAll(recommendations.getAnswer());
            recListView.setPrefHeight(Math.min(recommendations.getAnswer().size() * 28.0 + 5, 300));
            recListView.setMaxHeight(Region.USE_PREF_SIZE);
            container.getChildren().add(recListView);
        }
    }
}