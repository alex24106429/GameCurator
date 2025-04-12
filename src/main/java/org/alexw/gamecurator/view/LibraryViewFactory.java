package org.alexw.gamecurator.view;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.alexw.gamecurator.LibraryManager;
import org.alexw.gamecurator.misc.CacheManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

// Implement the ViewFactory interface
public class LibraryViewFactory implements ViewFactory {

    private final LibraryManager libraryManager;
    private final GameItemNodeFactory gameItemNodeFactory;

    public LibraryViewFactory(LibraryManager libraryManager, GameItemNodeFactory gameItemNodeFactory) {
        this.libraryManager = libraryManager;
        this.gameItemNodeFactory = gameItemNodeFactory;
    }

    // Rename method and add Override annotation
    @Override
    public Parent createView() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        VBox libraryContainer = new VBox(10);
        libraryContainer.setPadding(new Insets(10));
        libraryContainer.getStyleClass().add("game-list-container");
        libraryContainer.setId("libraryItemsContainer");

        // Load library items asynchronously after the view structure is created
        loadLibraryItems(libraryContainer);

        scrollPane.setContent(libraryContainer);
        return scrollPane;
    }

    // Make this public if MainController needs to trigger a refresh directly
    public void loadLibraryItems(VBox libraryContainer) {
        libraryContainer.getChildren().clear();
        Set<Integer> libraryItemIds = libraryManager.getLibraryItemIds();

        if (libraryItemIds.isEmpty()) {
            libraryContainer.getChildren().add(new Label("You have no games in your library yet."));
            return;
        }

        ProgressIndicator loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(40, 40);
        libraryContainer.getChildren().add(loadingIndicator);

        // Task to fetch/process cached data in the background
        Task<List<Node>> loadLibraryTask = new Task<>() {
            @Override
            protected List<Node> call() throws Exception {
                List<JsonObject> gameDataList = new ArrayList<>();
                List<String> errorMessages = new ArrayList<>();

                for (int gameId : libraryItemIds) {
                    String cachedGameJson = CacheManager.get("gameData_" + gameId);
                    if (cachedGameJson != null) {
                        try {
                            JsonObject game = JsonParser.parseString(cachedGameJson).getAsJsonObject();
                            // Add the ID to the JsonObject if it's missing, useful for sorting/debugging
                            if (!game.has("id")) {
                                game.addProperty("id", gameId);
                            }
                            gameDataList.add(game);
                        } catch (JsonSyntaxException | IllegalStateException e) {
                            System.err.println("Error parsing cached library game " + gameId + ": " + e.getMessage());
                            errorMessages.add("Error loading game ID: " + gameId + " (invalid data)");
                            CacheManager.remove("gameData_" + gameId); // Remove potentially corrupt cache entry
                        }
                    } else {
                        System.out.println("Data for game ID: " + gameId + " not found in cache. Cannot display.");
                        // Optionally add an error message here too if needed
                        // errorMessages.add("Data for game ID: " + gameId + " not found in cache.");
                    }
                }

                // Sort games alphabetically by name (if name exists)
                Collections.sort(gameDataList, Comparator.comparing(
                    game -> (game.has("name") && game.get("name").isJsonPrimitive()) ? game.get("name").getAsString().toLowerCase() : "zzz" // Put games without names last
                ));


                // Create UI Nodes on the FX Application Thread
                List<Node> gameNodes = new ArrayList<>();
                CountDownLatch latch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    try {
                        for (JsonObject game : gameDataList) {
                            try {
                                gameNodes.add(gameItemNodeFactory.createGameItemNode(game));
                            } catch (IOException e) {
                                System.err.println("Error creating node for library game: " + e.getMessage());
                                gameNodes.add(new Label("Error displaying game: " + getGameIdentifier(game)));
                            } catch (Exception e) {
                                 System.err.println("Unexpected error creating node for library game: " + e.getMessage());
                                 e.printStackTrace();
                                 gameNodes.add(new Label("Error displaying game: " + getGameIdentifier(game)));
                            }
                        }
                        // Add error messages at the end
                        for(String error : errorMessages) {
                            Label errorLabel = new Label(error);
                            errorLabel.setStyle("-fx-text-fill: orange;");
                            gameNodes.add(errorLabel);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
                latch.await(); // Wait for UI nodes to be created
                return gameNodes;
            }

            // Helper to get a game identifier for error messages
            private String getGameIdentifier(JsonObject game) {
                 if (game == null) return "Unknown";
                 if (game.has("name") && game.get("name").isJsonPrimitive()) return game.get("name").getAsString();
                 if (game.has("id") && game.get("id").isJsonPrimitive()) return "ID: " + game.get("id").getAsString();
                 return "Unknown Game";
            }
        };

        loadLibraryTask.setOnSucceeded(event -> {
            libraryContainer.getChildren().clear(); // Clear loading indicator
            List<Node> nodes = loadLibraryTask.getValue();
            if (nodes.isEmpty() && !libraryItemIds.isEmpty()){
                 // This means cache was missing for all items or errors occurred for all
                libraryContainer.getChildren().add(new Label("Could not load library items. Cache might be empty or data invalid."));
            } else if (nodes.isEmpty() && libraryItemIds.isEmpty()) {
                 // This case is handled by the initial check, but safe to include
                 libraryContainer.getChildren().add(new Label("You have no games in your library yet."));
            } else {
                libraryContainer.getChildren().addAll(nodes);
            }
        });

        loadLibraryTask.setOnFailed(event -> {
            libraryContainer.getChildren().clear(); // Clear loading indicator
            Throwable ex = loadLibraryTask.getException();
            ex.printStackTrace();
            Label errorLabel = new Label("Error loading library items: " + ex.getMessage());
            errorLabel.setStyle("-fx-text-fill: red;");
            libraryContainer.getChildren().add(errorLabel);
        });

        new Thread(loadLibraryTask).start();
    }
}
