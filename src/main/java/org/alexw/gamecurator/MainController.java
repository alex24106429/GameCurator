package org.alexw.gamecurator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import org.alexw.gamecurator.misc.APIClient; // Assume location
import org.alexw.gamecurator.util.DialogUtils;
import org.alexw.gamecurator.util.IconFactory;
import org.alexw.gamecurator.view.*; // Import view factories

import java.net.URL;
import java.util.*;
import java.util.prefs.Preferences;

public class MainController implements Initializable {

    // --- FXML Elements ---
    @FXML private BorderPane rootPane;
    @FXML private VBox navigationBox;
    @FXML private Label titleLabel;
    @FXML private StackPane contentPane; // Use StackPane for loading indicator overlay

    // --- State and Configuration ---
    private String currentPageId = "top_games"; // Default page
    private Button currentNavButton = null;
    final Preferences prefs = Preferences.userNodeForPackage(MainController.class); // Package-private or public getter if needed elsewhere directly
    final Gson gson = new Gson(); // Keep here or make injectable if needed elsewhere

    // Represents the structure of page definitions (Using top-level PageInfo class now)
    private final List<PageInfo> pages = List.of(
            new PageInfo("Top Games", "top_games", "STAR"),
            new PageInfo("New Games", "new_games", "CALENDAR"),
            new PageInfo("Search", "search", "SEARCH"),
            new PageInfo("Assistant", "assistant", "AUTO_FIX"),
            new PageInfo("Library", "library", "BOOKMARK"),
            new PageInfo("Settings", "settings", "SETTINGS")
    );

    // --- Services / Managers ---
    LibraryManager libraryManager; // Initialized in constructor or initialize
    GameItemNodeFactory gameItemNodeFactory;
    GameListViewFactory gameListViewFactory;
    SearchViewFactory searchViewFactory;
    LibraryViewFactory libraryViewFactory;
    AssistantViewFactory assistantViewFactory;
    SettingsViewFactory settingsViewFactory;


    // --- Initialization ---
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize managers and factories that depend on prefs/gson/this
        libraryManager = new LibraryManager(prefs, gson);
        gameItemNodeFactory = new GameItemNodeFactory(libraryManager, this); // Pass 'this' for actions
        gameListViewFactory = new GameListViewFactory(gameItemNodeFactory);
        searchViewFactory = new SearchViewFactory(gameItemNodeFactory);
        libraryViewFactory = new LibraryViewFactory(libraryManager, gameItemNodeFactory);
        assistantViewFactory = new AssistantViewFactory(libraryManager, prefs);
        settingsViewFactory = new SettingsViewFactory(prefs, libraryManager, this); // Pass 'this' for reset->switchPage

        setupNavigationBar();
        // String lastPage = prefs.get("lastPage", "top_games"); // Optional: Load last viewed page
        // switchPage(lastPage);
        switchPage(currentPageId); // Load initial page
    }

    // Make public if needed e.g. after reset, though switchPage("settings") handles it now
    /* public */ void setupNavigationBar() {
        navigationBox.getChildren().clear();
        for (PageInfo page : pages) {
            Button navButton = createNavButton(page);
            navigationBox.getChildren().add(navButton);
            if (page.getId().equals(currentPageId)) { // Use getter
                currentNavButton = navButton;
                // Apply active style during setup if it's the initial page
                // Ensure this runs after CSS is applied
                Platform.runLater(() -> {
                    if (navButton.getScene() != null) { // Check if node is added to scene
                         navButton.getStyleClass().add("active");
                    } else {
                        // If scene not ready, listen for it
                        navButton.sceneProperty().addListener((obs, oldScene, newScene) -> {
                            if (newScene != null) {
                                Platform.runLater(() -> navButton.getStyleClass().add("active"));
                            }
                        });
                    }
                });
            }
        }
    }

    private Button createNavButton(PageInfo page) {
        // Use IconFactory
        Node iconNode = IconFactory.createIcon(page.getIconName(), IconFactory.NAV_ICON_SIZE);
        Button button = new Button(page.getTitle()); // Use getter
        button.setGraphic(iconNode);
        button.setContentDisplay(ContentDisplay.TOP);
        button.setUserData(page.getId()); // Use getter
        button.setOnAction(this::handleNavigation);
        button.getStyleClass().add("navbar-item");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    // --- Navigation ---
    @FXML
    private void handleNavigation(ActionEvent event) {
        if (event.getSource() instanceof Button) {
            Button clickedButton = (Button) event.getSource();
            String targetPageId = (String) clickedButton.getUserData();
            // Only switch if the target is different AND the button isn't already disabled (meaning it's the current page)
            if (targetPageId != null && !targetPageId.equals(currentPageId) && !clickedButton.isDisabled()) {
                // Style update happens immediately on click
                if (currentNavButton != null) {
                    currentNavButton.getStyleClass().remove("active");
                    // No need to re-enable here, switchPage handles enabling/disabling
                }
                clickedButton.getStyleClass().add("active");
                currentNavButton = clickedButton; // Update the reference to the *newly clicked* button

                switchPage(targetPageId); // Switch content and update disabled states
                // prefs.put("lastPage", targetPageId); // Optional: Persist last viewed page
            }
        }
    }

    // --- Page Switching ---
    // Made public so SettingsViewFactory can call it after reset
    public void switchPage(String pageId) {
        System.out.println("Switching to page: " + pageId);
        this.currentPageId = pageId; // Update current page state FIRST

        // Update Title
        Optional<PageInfo> pageInfo = pages.stream().filter(p -> p.getId().equals(pageId)).findFirst();
        titleLabel.setText(pageInfo.map(PageInfo::getTitle).orElse("Unknown Page")); // Use method reference

        // Update Navigation Button States (Disable current, enable others)
        for (Node node : navigationBox.getChildren()) {
            if (node instanceof Button) {
                Button navButton = (Button) node;
                String buttonPageId = (String) navButton.getUserData();
                if (buttonPageId != null) {
                    // Disable the button if its page ID matches the new current page ID
                    navButton.setDisable(buttonPageId.equals(this.currentPageId));
                }
            }
        }

        // --- Content Loading ---
        contentPane.getChildren().clear(); // Clear previous content immediately

        ProgressIndicator loadingIndicator = new ProgressIndicator(-1.0); // Indeterminate
        loadingIndicator.setMaxSize(50, 50);
        contentPane.getChildren().add(loadingIndicator); // Add loading indicator
        StackPane.setAlignment(loadingIndicator, Pos.CENTER); // Center indicator

        // Task to load page content (API calls or complex view creation)
        Task<Parent> loadTask = new Task<>() {
            @Override
            protected Parent call() throws Exception {
                // Fetch data or create view using factories
                // IMPORTANT: View creation involving FXML or complex node structures
                // should ideally happen on the FX thread. Factories should either:
                // 1. Be designed to be called safely from background (simple layouts)
                // 2. Return data, and view creation happens in setOnSucceeded
                // 3. Use Platform.runLater internally (can be complex)
                // Here, we assume factories handle threading or are simple enough.
                // For API calls, they happen here in the background.
                switch (pageId) {
                    case "top_games": {
                        String topJson = APIClient.getTopGames().join(); // Fetch data in background
                        // Create view using data - assuming factory is safe or handles FX thread
                        return gameListViewFactory.createGameListView(topJson);
                    }
                    case "new_games": {
                        String newJson = APIClient.getNewGames().join(); // Fetch data in background
                         // Create view using data
                        return gameListViewFactory.createGameListView(newJson);
                    }
                    case "search":
                        // Factory creates the view structure; search itself is async on user action
                        return searchViewFactory.createSearchView();
                    case "assistant":
                        // Factory creates view; recommendations are async on user action
                        return assistantViewFactory.createAssistantView();
                    case "library":
                        // Factory creates view; loading items is async within the factory/view itself
                        return libraryViewFactory.createLibraryView();
                    case "settings":
                        // Factory creates static settings view
                        return settingsViewFactory.createSettingsView();
                    default:
                        // Safe fallback view
                        return new VBox(new Label("Content for " + pageId + " not implemented."));
                }
            }
        };

        loadTask.setOnSucceeded(event -> {
            Parent pageContent = loadTask.getValue();
            contentPane.getChildren().clear(); // Remove indicator
            if (pageContent != null) {
                contentPane.getChildren().add(pageContent); // Add loaded content
                StackPane.setAlignment(pageContent, Pos.TOP_LEFT); // Align content to top-left usually
            } else {
                 // Handle null content case, maybe show an error
                 Label errorLabel = new Label("Error: Failed to load page content for " + pageId);
                 errorLabel.setStyle("-fx-text-fill: red;");
                 contentPane.getChildren().add(errorLabel);
                 StackPane.setAlignment(errorLabel, Pos.CENTER);
            }
        });

        loadTask.setOnFailed(event -> {
            contentPane.getChildren().clear(); // Remove indicator
            Throwable ex = loadTask.getException();
            ex.printStackTrace(); // Log the full error
            Label errorLabel = new Label("Error loading page: " + pageId + "\n" + ex.getMessage());
            errorLabel.setWrapText(true);
            errorLabel.setStyle("-fx-text-fill: red;");
            contentPane.getChildren().add(errorLabel);
            StackPane.setAlignment(errorLabel, Pos.CENTER);
        });

        new Thread(loadTask).start(); // Run the task on a background thread
    }


    // --- Library Interaction Methods (Called by GameItemNodeFactory) ---

    // This method orchestrates the library change and potential UI refresh
    public void handleLibraryToggle(int gameId, JsonObject gameData) {
        boolean wasInLibrary = libraryManager.isInLibrary(gameId);
        boolean changed;
        if (wasInLibrary) {
            changed = libraryManager.removeLibraryItem(gameId);
        } else {
            // Pass gameData for caching when adding
            changed = libraryManager.addLibraryItem(gameId, gameData);
        }

        // If the state actually changed, refresh relevant views
        if (changed) {
             refreshCurrentPageIf("library"); // Refresh library view if currently active
             refreshCurrentPageIf("assistant"); // Refresh assistant view (button state/status might change)
        }
         // The button state within the specific GameItemNode is updated
         // by the GameItemNodeFactory's event handler itself immediately after toggle.
    }


    // --- Share Action Method (Called by GameItemNodeFactory) ---

    public void handleShareGame(JsonObject game) {
        if (game == null) return; // Safety check

        String gameName = "this game";
         if (game.has("name") && game.get("name").isJsonPrimitive()) {
             gameName = game.get("name").getAsString();
         }

        String gameSlugOrId = "unknown";
        if (game.has("slug") && game.get("slug").isJsonPrimitive()) {
            gameSlugOrId = game.get("slug").getAsString();
        } else if (game.has("id") && game.get("id").isJsonPrimitive()) {
            try {
                gameSlugOrId = game.get("id").getAsString(); // Prefer slug if available
            } catch (UnsupportedOperationException | NumberFormatException e) { /* ignore if not string/number */ }
        }

        // Use a placeholder URL structure, replace with actual if known (e.g., RAWG)
        String gameUrl = "https://rawg.io/games/" + gameSlugOrId; // Example URL structure
        if (gameSlugOrId.equals("unknown")) {
            gameUrl = "[Link not available]"; // Fallback if no ID/slug
        }

        String shareText = "Check out this game: " + gameName + "\n" + gameUrl;
        System.out.println("Sharing: " + shareText);

        try {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(shareText);
            clipboard.setContent(content);
            DialogUtils.showInfoDialog("Shared!", gameName + " link copied to clipboard."); // Use DialogUtils
        } catch (Exception e) {
            // Catch potential SecurityException or other issues accessing clipboard
            System.err.println("Could not copy to clipboard: " + e.getMessage());
            DialogUtils.showErrorDialog("Share Error", "Could not copy link to clipboard."); // Use DialogUtils
        }
    }

    /**
     * Refreshes the content of the current page if its ID matches the given pageId.
     * @param pageIdToRefresh The ID of the page to potentially refresh.
     */
    public void refreshCurrentPageIf(String pageIdToRefresh) {
        if (pageIdToRefresh != null && pageIdToRefresh.equals(this.currentPageId)) {
            System.out.println("Refreshing current page: " + pageIdToRefresh);
            // Ensure the refresh logic also correctly sets the disabled state after reload
            Platform.runLater(() -> switchPage(this.currentPageId));
        }
    }
}
