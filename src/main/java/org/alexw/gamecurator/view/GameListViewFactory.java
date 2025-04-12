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
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class GameListViewFactory {

    private final GameItemNodeFactory gameItemNodeFactory;

    // Define the list of available genres
    public static final Set<String> AVAILABLE_GENRES = Set.of(
            "Action", "Indie", "Adventure", "RPG", "Strategy", "Shooter", "Casual",
            "Simulation", "Puzzle", "Arcade", "Platformer", "Massively Multiplayer",
            "Racing", "Sports", "Fighting", "Family"
    );


    public GameListViewFactory(GameItemNodeFactory gameItemNodeFactory) {
        this.gameItemNodeFactory = gameItemNodeFactory;
    }

    public Parent createGameListView(String jsonGameData, Set<String> selectedGenres, Integer minPlaytime, Integer maxPlaytime) {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        VBox gameListContainer = new VBox();
        gameListContainer.setSpacing(10);
        gameListContainer.setPadding(new Insets(10));
        gameListContainer.getStyleClass().add("game-list-container");

        int gamesAdded = 0;

        if (jsonGameData == null || jsonGameData.isEmpty()) {
            gameListContainer.getChildren().add(new Label("No game data provided."));
        } else {
            try {
                JsonElement parsedElement = JsonParser.parseString(jsonGameData);
                JsonArray gamesArray = null;

                if (parsedElement.isJsonArray()) {
                    gamesArray = parsedElement.getAsJsonArray();
                } else if (parsedElement.isJsonObject()) {
                    JsonObject rootObject = parsedElement.getAsJsonObject();
                    if (rootObject.has("results") && rootObject.get("results").isJsonArray()) {
                        gamesArray = rootObject.getAsJsonArray("results");
                    } else {
                        System.err.println("JSON data is an object but does not contain a 'results' array.");
                        gameListContainer.getChildren().add(new Label("Unexpected data format received."));
                    }
                } else {
                    System.err.println("Error: Provided JSON data is not a valid JSON array or expected object structure.");
                    gameListContainer.getChildren().add(new Label("Invalid game data format."));
                }

                if (gamesArray != null) {
                    for (JsonElement gameElement : gamesArray) {
                        if (gameElement.isJsonObject()) {
                            JsonObject game = gameElement.getAsJsonObject();

                            if (passesFilters(game, selectedGenres, minPlaytime, maxPlaytime)) {
                                try {
                                    Node gameItemNode = gameItemNodeFactory.createGameItemNode(game);
                                    gameListContainer.getChildren().add(gameItemNode);
                                    gamesAdded++;
                                } catch (IOException e) {
                                    System.err.println("Error creating game item node: " + e.getMessage());
                                } catch (Exception e) {
                                    System.err.println("Unexpected error creating game item node: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            }

                        } else {
                            System.err.println("Warning: Found non-object element in games array: " + gameElement);
                        }
                    }

                    if (gamesAdded == 0 && gamesArray.size() > 0) {
                         gameListContainer.getChildren().add(new Label("No games match the current filters."));
                    } else if (gamesArray.isEmpty()) {
                         gameListContainer.getChildren().add(new Label("No games found in the source data."));
                    }

                }

            } catch (JsonSyntaxException e) {
                System.err.println("Error parsing game list JSON: " + e.getMessage());
                gameListContainer.getChildren().add(new Label("Error loading game data (invalid JSON)."));
            } catch (IllegalStateException e) {
                 System.err.println("Error processing JSON structure: " + e.getMessage());
                 gameListContainer.getChildren().add(new Label("Error processing game data structure."));
            } catch (Exception e) {
                System.err.println("Unexpected error processing game list data: " + e.getMessage());
                e.printStackTrace();
                gameListContainer.getChildren().add(new Label("An unexpected error occurred while loading games."));
            }
        }

        // Handle case where initial data was empty or null
        if (gameListContainer.getChildren().isEmpty() && gamesAdded == 0) {
            // This covers cases where parsing failed or filters removed everything
            if (gameListContainer.getChildren().stream().noneMatch(node -> node instanceof Label)) {
                 gameListContainer.getChildren().add(new Label("No games to display."));
            }
        }


        scrollPane.setContent(gameListContainer);
        return scrollPane;
    }

    // Helper method to check if a game passes the filters
    private boolean passesFilters(JsonObject game, Set<String> selectedGenres, Integer minPlaytime, Integer maxPlaytime) {
        // Genre Filter
        if (selectedGenres != null && !selectedGenres.isEmpty()) {
            if (!game.has("genres") || !game.get("genres").isJsonArray()) {
                return false;
            }
            JsonArray gameGenresArray = game.getAsJsonArray("genres");
            Set<String> gameGenreNames = StreamSupport.stream(gameGenresArray.spliterator(), false)
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject)
                    .filter(g -> g.has("name") && g.get("name").isJsonPrimitive())
                    .map(g -> g.get("name").getAsString())
                    .collect(Collectors.toSet());

            if (Collections.disjoint(gameGenreNames, selectedGenres)) {
                 return false;
            }
        }

        // Playtime Filter
        if (minPlaytime != null || maxPlaytime != null) {
            if (!game.has("playtime") || !game.get("playtime").isJsonPrimitive() || !game.get("playtime").getAsJsonPrimitive().isNumber()) {
                // If filtering by playtime, game must have a valid playtime number
                 if (!(minPlaytime != null && minPlaytime == 0 && game.has("playtime") && game.get("playtime").getAsInt() == 0)) {
                    return false;
                 }
            }

            int playtime = game.get("playtime").getAsInt();

            if (minPlaytime != null && playtime < minPlaytime) {
                return false;
            }
            if (maxPlaytime != null && playtime > maxPlaytime) {
                return false;
            }
        }

        return true;
    }
}
