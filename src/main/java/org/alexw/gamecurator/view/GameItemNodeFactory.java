package org.alexw.gamecurator.view;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import org.alexw.gamecurator.LibraryManager;
import org.alexw.gamecurator.MainController; // To call toggle/share methods
import org.alexw.gamecurator.util.IconFactory; // Use IconFactory

import java.io.IOException;
import java.util.stream.Collectors;

public class GameItemNodeFactory {

    // Need access to LibraryManager and potentially MainController for actions
    private final LibraryManager libraryManager;
    private final MainController mainController; // Pass MainController for actions

    public GameItemNodeFactory(LibraryManager libraryManager, MainController mainController) {
        this.libraryManager = libraryManager;
        this.mainController = mainController;
    }

    public Node createGameItemNode(JsonObject game) throws IOException {
        // Ensure game is not null
        if (game == null) {
            System.err.println("Error: Cannot create game item node from null game object.");
            return new Label("Error: Invalid game data");
        }

        // Load FXML from the correct resources path
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/alexw/gamecurator/GameItem.fxml"));
        Parent gameItemRoot = loader.load();

        // Safely access FXML elements
        ImageView gameImageView = (ImageView) loader.getNamespace().get("gameImageView");
        Label gameNameLabel = (Label) loader.getNamespace().get("gameNameLabel");
        Label gameReleaseLabel = (Label) loader.getNamespace().get("gameReleaseLabel");
        Text gameDetailsText = (Text) loader.getNamespace().get("gameDetailsText");
        Button libraryButton = (Button) loader.getNamespace().get("libraryButton");
        Button shareButton = (Button) loader.getNamespace().get("shareButton");

        // Check if elements were loaded correctly
        if (gameImageView == null || gameNameLabel == null || gameReleaseLabel == null ||
            gameDetailsText == null || libraryButton == null || shareButton == null) {
            System.err.println("Error: Could not find all expected elements in GameItem.fxml");
            // Return the partially loaded root or an error node
            return gameItemRoot != null ? gameItemRoot : new Label("Error loading FXML components");
        }


        gameNameLabel.setText(game.has("name") && game.get("name").isJsonPrimitive() ? game.get("name").getAsString() : "N/A");
        gameReleaseLabel.setText(game.has("released") && game.get("released").isJsonPrimitive() ? "Released: " + game.get("released").getAsString() : "Release: N/A");

        StringBuilder details = new StringBuilder();
        if (game.has("rating") && game.get("rating").isJsonPrimitive()) {
            try {
                 details.append(String.format("%.1f", game.get("rating").getAsDouble())).append(" ★ | ");
            } catch (NumberFormatException e) { /* ignore if not a number */ }
        }
        if (game.has("genres") && game.get("genres").isJsonArray()) {
            details.append(
                    game.getAsJsonArray("genres").asList().stream()
                            .filter(JsonElement::isJsonObject)
                            .map(g -> g.getAsJsonObject().has("name") ? g.getAsJsonObject().get("name").getAsString() : "")
                            .filter(name -> !name.isEmpty())
                            .collect(Collectors.joining(", "))
            ).append(" | ");
        }
        if (game.has("platforms") && game.get("platforms").isJsonArray()) {
            details.append(
                    game.getAsJsonArray("platforms").asList().stream()
                            .filter(p -> p.isJsonObject() && p.getAsJsonObject().has("platform") && p.getAsJsonObject().get("platform").isJsonObject())
                            .map(p -> p.getAsJsonObject().getAsJsonObject("platform").has("name") ? p.getAsJsonObject().getAsJsonObject("platform").get("name").getAsString() : "")
                            .filter(name -> !name.isEmpty())
                            .collect(Collectors.joining(", "))
            );
        }
        String detailsStr = details.toString().trim();
        if (detailsStr.endsWith("|")) {
            detailsStr = detailsStr.substring(0, detailsStr.length() - 1).trim();
        }
         if (detailsStr.endsWith("★ |")) { // Clean up trailing separator if only rating exists
            detailsStr = detailsStr.substring(0, detailsStr.length() - 2).trim();
        }
        gameDetailsText.setText(detailsStr.isEmpty() ? "No details available" : detailsStr);

        if (game.has("background_image") && game.get("background_image").isJsonPrimitive()) {
             String imageUrl = game.get("background_image").getAsString();
             if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.equalsIgnoreCase("null")) {
                Image gameImage = new Image(imageUrl, true); // Load in background
                gameImageView.setImage(gameImage);
                gameImage.errorProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal) {
                        System.err.println("Failed to load image: " + imageUrl);
                        // Optionally set a placeholder image on error
                        // gameImageView.setImage(new Image(getClass().getResource("/path/to/placeholder.png").toExternalForm()));
                        gameImageView.setImage(null); // Clear on error
                    }
                });
                // Optional: Show placeholder while loading
                // gameImageView.setImage(placeholderImage);
                // gameImage.progressProperty().addListener((obs, oldProgress, newProgress) -> {
                //    if (newProgress.doubleValue() == 1.0) {
                //        gameImageView.setImage(gameImage); // Set final image when loaded
                //    }
                // });
             } else {
                 gameImageView.setImage(null); // No valid image URL
             }
        } else {
            gameImageView.setImage(null); // No image field
        }

        shareButton.setGraphic(IconFactory.createIcon("SHARE", IconFactory.BUTTON_ICON_SIZE));
        shareButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        shareButton.setTooltip(new Tooltip("Share Game"));

        int gameId = -1;
        if (game.has("id") && game.get("id").isJsonPrimitive()) {
             try {
                gameId = game.get("id").getAsInt();
             } catch (NumberFormatException e) {
                 System.err.println("Warning: Game object 'id' is not a valid integer: " + game.get("id"));
                 gameId = -1; // Treat as invalid ID
             }
        }

        if (gameId != -1) {
            updateLibraryButtonState(gameId, libraryButton); // Initial state

            int finalGameId = gameId; // For lambda
            // Use the MainController's handler methods
            libraryButton.setOnAction(event -> {
                mainController.handleLibraryToggle(finalGameId, game);
                // Re-update *this* button's state immediately after the action
                updateLibraryButtonState(finalGameId, libraryButton);
            });
            shareButton.setOnAction(event -> mainController.handleShareGame(game));
        } else {
            System.err.println("Warning: Game object missing or invalid 'id' field: " + game);
            libraryButton.setDisable(true);
            shareButton.setDisable(true);
            libraryButton.setText("Error");
            libraryButton.setGraphic(null);
        }


        return gameItemRoot;
    }

    // Keep this utility tied to the factory that creates the button
    public void updateLibraryButtonState(int gameId, Button button) {
        if (button == null) return; // Safety check
        Platform.runLater(() -> { // Ensure UI update on FX thread
            boolean inLibrary = libraryManager.isInLibrary(gameId);
            String text = inLibrary ? "In Library" : "Add Library";
            String iconName = inLibrary ? "REMOVE" : "ADD";

            button.setText(text);
            button.setGraphic(IconFactory.createIcon(iconName, IconFactory.BUTTON_ICON_SIZE));
            button.setContentDisplay(ContentDisplay.LEFT);
            button.setTooltip(new Tooltip(inLibrary ? "Remove from Library" : "Add to Library"));
            // Optional: Add/remove a CSS class for styling active state
             button.getStyleClass().remove("in-library");
             button.getStyleClass().remove("not-in-library");
            if (inLibrary) {
                 button.getStyleClass().add("in-library"); // Add a specific style class
            } else {
                 button.getStyleClass().add("not-in-library"); // Add a specific style class
            }
        });
    }
}
