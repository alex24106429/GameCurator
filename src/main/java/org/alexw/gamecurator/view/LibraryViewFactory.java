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

public class LibraryViewFactory implements ViewFactory {

    private final LibraryManager libraryManager;
    private final GameItemNodeFactory gameItemNodeFactory;

    public LibraryViewFactory(LibraryManager libraryManager, GameItemNodeFactory gameItemNodeFactory) {
        this.libraryManager = libraryManager;
        this.gameItemNodeFactory = gameItemNodeFactory;
    }

    @Override
    public Parent createView() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        VBox libraryContainer = new VBox(10);
        libraryContainer.setPadding(new Insets(10));
        libraryContainer.getStyleClass().add("game-list-container");
        libraryContainer.setId("libraryItemsContainer");

        loadLibraryItems(libraryContainer);

        scrollPane.setContent(libraryContainer);
        return scrollPane;
    }

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

                            if (!game.has("id")) {
                                game.addProperty("id", gameId);
                            }
                            gameDataList.add(game);
                        } catch (JsonSyntaxException | IllegalStateException e) {
                            System.err.println("Error parsing cached library game " + gameId + ": " + e.getMessage());
                            errorMessages.add("Error loading game ID: " + gameId + " (invalid data)");
                            CacheManager.remove("gameData_" + gameId); 
                        }
                    } else {
                        System.out.println("Data for game ID: " + gameId + " not found in cache. Cannot display.");

                    }
                }

                Collections.sort(gameDataList, Comparator.comparing(
                    game -> (game.has("name") && game.get("name").isJsonPrimitive()) ? game.get("name").getAsString().toLowerCase() : "zzz" 
                ));

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

                        for(String error : errorMessages) {
                            Label errorLabel = new Label(error);
                            errorLabel.setStyle("-fx-text-fill: orange;");
                            gameNodes.add(errorLabel);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
                latch.await(); 
                return gameNodes;
            }

            private String getGameIdentifier(JsonObject game) {
                 if (game == null) return "Unknown";
                 if (game.has("name") && game.get("name").isJsonPrimitive()) return game.get("name").getAsString();
                 if (game.has("id") && game.get("id").isJsonPrimitive()) return "ID: " + game.get("id").getAsString();
                 return "Unknown Game";
            }
        };

        loadLibraryTask.setOnSucceeded(event -> {
            libraryContainer.getChildren().clear(); 
            List<Node> nodes = loadLibraryTask.getValue();
            if (nodes.isEmpty() && !libraryItemIds.isEmpty()){

                libraryContainer.getChildren().add(new Label("Could not load library items. Cache might be empty or data invalid."));
            } else if (nodes.isEmpty() && libraryItemIds.isEmpty()) {

                 libraryContainer.getChildren().add(new Label("You have no games in your library yet."));
            } else {
                libraryContainer.getChildren().addAll(nodes);
            }
        });

        loadLibraryTask.setOnFailed(event -> {
            libraryContainer.getChildren().clear(); 
            Throwable ex = loadLibraryTask.getException();
            ex.printStackTrace();
            Label errorLabel = new Label("Error loading library items: " + ex.getMessage());
            errorLabel.setStyle("-fx-text-fill: red;");
            libraryContainer.getChildren().add(errorLabel);
        });

        new Thread(loadLibraryTask).start();
    }
}