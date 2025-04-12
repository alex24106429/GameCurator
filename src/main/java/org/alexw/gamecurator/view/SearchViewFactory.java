package org.alexw.gamecurator.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.alexw.gamecurator.misc.APIClient;
import org.alexw.gamecurator.util.IconFactory;

import java.io.IOException;

public class SearchViewFactory {

    private final GameItemNodeFactory gameItemNodeFactory;

    public SearchViewFactory(GameItemNodeFactory gameItemNodeFactory) {
        this.gameItemNodeFactory = gameItemNodeFactory;
    }

    public Parent createSearchView() {
        VBox searchPane = new VBox(10);
        searchPane.setPadding(new Insets(10));
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search games...");
        searchBox.setId("searchBox");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        VBox resultsContainer = new VBox(10);
        resultsContainer.setPadding(new Insets(5));
        resultsContainer.setId("gameItemContainer");
        scrollPane.setContent(resultsContainer);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Button searchButton = new Button("Search");
        searchButton.setGraphic(IconFactory.createIcon("SEARCH", IconFactory.BUTTON_ICON_SIZE));
        searchButton.setContentDisplay(ContentDisplay.LEFT);
        searchButton.setDefaultButton(true);

        // Link actions to the performSearch method within this factory
        searchBox.setOnAction(event -> performSearch(searchBox.getText(), resultsContainer, searchButton));
        searchButton.setOnAction(event -> performSearch(searchBox.getText(), resultsContainer, searchButton));

        HBox searchArea = new HBox(10, searchBox, searchButton);
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        searchPane.getChildren().addAll(searchArea, scrollPane);
        return searchPane;
    }

    private void performSearch(String query, VBox resultsContainer, Button searchButton) {
        resultsContainer.getChildren().clear();
        String trimmedQuery = (query != null) ? query.trim() : "";

        if (trimmedQuery.length() < 2 || trimmedQuery.length() > 50) {
            resultsContainer.getChildren().add(new Label("Please enter a search query (2-50 characters)."));
            return;
        }

        searchButton.setDisable(true);
        searchButton.setText("Searching...");
        ProgressIndicator loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(40, 40);
        // Add indicator centered? Or just at the top? Top is simpler.
        resultsContainer.getChildren().add(loadingIndicator);


        // Use CompletableFuture for API call
        APIClient.searchGames(trimmedQuery)
                .whenCompleteAsync((jsonResult, error) -> {
                    resultsContainer.getChildren().clear();
                    searchButton.setDisable(false);
                    searchButton.setText("Search");

                    if (error != null) {
                        resultsContainer.getChildren().add(new Label("Error performing search: " + error.getMessage()));
                        error.printStackTrace();
                    } else if (jsonResult == null || jsonResult.isEmpty()) {
                        resultsContainer.getChildren().add(new Label("Your search returned no results."));
                    } else {
                        try {
                            JsonObject resultObject = JsonParser.parseString(jsonResult).getAsJsonObject();
                            if (!resultObject.has("results") || !resultObject.get("results").isJsonArray()) {
                                throw new JsonSyntaxException("Expected 'results' array not found in search response");
                            }
                            JsonArray gamesArray = resultObject.getAsJsonArray("results");

                            if (gamesArray.isEmpty()) {
                                resultsContainer.getChildren().add(new Label("Your search for '" + trimmedQuery + "' returned no results."));
                                return;
                            }
                            resultsContainer.getChildren().add(new Label("Found " + gamesArray.size() + " results for '" + trimmedQuery + "':")); // Show result count
                            for (JsonElement gameElement : gamesArray) {
                                if (gameElement.isJsonObject()) {
                                    JsonObject game = gameElement.getAsJsonObject();
                                    try {
                                        // Use the injected factory to create nodes
                                        resultsContainer.getChildren().add(gameItemNodeFactory.createGameItemNode(game));
                                    } catch (IOException e) {
                                        System.err.println("Error creating game item node during search: " + e.getMessage());
                                        resultsContainer.getChildren().add(new Label("Error loading game item."));
                                    } catch (Exception e) { // Catch broader exceptions during node creation
                                         System.err.println("Unexpected error creating game item node during search: " + e.getMessage());
                                         e.printStackTrace();
                                         resultsContainer.getChildren().add(new Label("Error displaying a game item."));
                                    }
                                } else {
                                    System.err.println("Warning: Found non-object element in search results array: " + gameElement);
                                }
                            }
                        } catch (JsonSyntaxException e) {
                            System.err.println("Error parsing search results JSON: " + e.getMessage());
                            resultsContainer.getChildren().add(new Label("Error displaying search results (invalid format)."));
                        } catch (IllegalStateException e) {
                             System.err.println("Error processing search results structure: " + e.getMessage());
                             resultsContainer.getChildren().add(new Label("Error processing search results structure."));
                        } catch (Exception e) { // Catch any other unexpected errors
                            System.err.println("Unexpected error processing search results: " + e.getMessage());
                            e.printStackTrace();
                            resultsContainer.getChildren().add(new Label("An unexpected error occurred displaying search results."));
                        }
                    }
                }, Platform::runLater);
    }
}
