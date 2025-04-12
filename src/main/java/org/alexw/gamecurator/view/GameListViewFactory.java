package org.alexw.gamecurator.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class GameListViewFactory {

    private final GameItemNodeFactory gameItemNodeFactory;

    public GameListViewFactory(GameItemNodeFactory gameItemNodeFactory) {
        this.gameItemNodeFactory = gameItemNodeFactory;
    }

    public Parent createGameListView(String jsonGameData) {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        VBox gameListContainer = new VBox();
        gameListContainer.setSpacing(10);
        gameListContainer.setPadding(new Insets(10));
        gameListContainer.getStyleClass().add("game-list-container");

        if (jsonGameData == null || jsonGameData.isEmpty()) {
            gameListContainer.getChildren().add(new Label("No game data provided."));
        } else {
            try {
                JsonElement parsedElement = JsonParser.parseString(jsonGameData);

                if (parsedElement.isJsonArray()) {
                    JsonArray gamesArray = parsedElement.getAsJsonArray();
                    if (gamesArray.isEmpty()) {
                        gameListContainer.getChildren().add(new Label("No games found."));
                    } else {
                        for (JsonElement gameElement : gamesArray) {
                            if (gameElement.isJsonObject()) {
                                JsonObject game = gameElement.getAsJsonObject();
                                try {
                                    Node gameItemNode = gameItemNodeFactory.createGameItemNode(game);
                                    gameListContainer.getChildren().add(gameItemNode);
                                } catch (IOException e) {
                                    System.err.println("Error creating game item node: " + e.getMessage());
                                    gameListContainer.getChildren().add(new Label("Error loading game item."));
                                } catch (Exception e) { // Catch broader exceptions during node creation
                                     System.err.println("Unexpected error creating game item node: " + e.getMessage());
                                     e.printStackTrace();
                                     gameListContainer.getChildren().add(new Label("Error displaying game item."));
                                }
                            } else {
                                System.err.println("Warning: Found non-object element in games array: " + gameElement);
                            }
                        }
                    }
                } else {
                     // Handle cases where the root might be an object containing the array, e.g., RAWG's "results"
                     if (parsedElement.isJsonObject()) {
                         JsonObject rootObject = parsedElement.getAsJsonObject();
                         if (rootObject.has("results") && rootObject.get("results").isJsonArray()) {
                             // Recursively call with the "results" array
                             // This is a common pattern for APIs like RAWG
                             return createGameListView(rootObject.get("results").toString());
                         } else {
                            System.err.println("JSON data is an object but does not contain a 'results' array.");
                            gameListContainer.getChildren().add(new Label("Unexpected data format received."));
                         }
                     } else {
                        System.err.println("Error: Provided JSON data is not a valid JSON array or expected object structure.");
                        gameListContainer.getChildren().add(new Label("Invalid game data format."));
                     }
                }
            } catch (JsonSyntaxException e) {
                System.err.println("Error parsing game list JSON: " + e.getMessage());
                gameListContainer.getChildren().add(new Label("Error loading game data (invalid JSON)."));
            } catch (IllegalStateException e) {
                 System.err.println("Error processing JSON structure: " + e.getMessage());
                 gameListContainer.getChildren().add(new Label("Error processing game data structure."));
            } catch (Exception e) { // Catch any other unexpected errors during parsing/processing
                System.err.println("Unexpected error processing game list data: " + e.getMessage());
                e.printStackTrace();
                gameListContainer.getChildren().add(new Label("An unexpected error occurred while loading games."));
            }
        }
        scrollPane.setContent(gameListContainer);
        return scrollPane;
    }
}
