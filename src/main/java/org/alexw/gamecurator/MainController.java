package org.alexw.gamecurator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
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
import org.alexw.gamecurator.misc.APIClient;
import org.alexw.gamecurator.util.DialogUtils;
import org.alexw.gamecurator.util.IconFactory;
import org.alexw.gamecurator.view.*;
import org.controlsfx.control.CheckComboBox; // Import for CheckComboBox

import java.net.URL;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class MainController implements Initializable {

    // --- FXML Elements ---
    @FXML private BorderPane rootPane;
    @FXML private VBox navigationBox;
    @FXML private Label titleLabel;
    @FXML private StackPane contentPane; // Use StackPane for loading indicator overlay
    @FXML private HBox filterBar; // Container for filter controls (Assuming HBox, add to FXML later)
    @FXML private CheckComboBox<String> genreFilterComboBox; // Genre filter control (Add to FXML)
    @FXML private TextField minPlaytimeFilterField; // Min playtime filter (Add to FXML)
    @FXML private TextField maxPlaytimeFilterField; // Max playtime filter (Add to FXML)
    @FXML private Button clearFiltersButton; // Button to clear filters (Add to FXML)


    // --- State and Configuration ---
    private String currentPageId = "top_games"; // Default page
    private Button currentNavButton = null;
    final Preferences prefs = Preferences.userNodeForPackage(MainController.class);
    final Gson gson = new Gson();

    // Filter State
    private Set<String> selectedGenres = new HashSet<>();
    private Integer minPlaytime = null;
    private Integer maxPlaytime = null;

    private final List<PageInfo> pages = List.of(
            new PageInfo("Top Games", "top_games", "STAR"),
            new PageInfo("New Games", "new_games", "CALENDAR"),
            new PageInfo("Search", "search", "SEARCH"),
            new PageInfo("Assistant", "assistant", "AUTO_FIX"),
            new PageInfo("Library", "library", "BOOKMARK"),
            new PageInfo("Settings", "settings", "SETTINGS")
    );

    // --- Services / Managers ---
    LibraryManager libraryManager;
    GameItemNodeFactory gameItemNodeFactory;
    GameListViewFactory gameListViewFactory;
    SearchViewFactory searchViewFactory;
    LibraryViewFactory libraryViewFactory;
    AssistantViewFactory assistantViewFactory;
    SettingsViewFactory settingsViewFactory;


    // --- Initialization ---
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize managers and factories
        libraryManager = new LibraryManager(prefs, gson);
        gameItemNodeFactory = new GameItemNodeFactory(libraryManager, this);
        gameListViewFactory = new GameListViewFactory(gameItemNodeFactory);
        searchViewFactory = new SearchViewFactory(gameItemNodeFactory);
        libraryViewFactory = new LibraryViewFactory(libraryManager, gameItemNodeFactory);
        assistantViewFactory = new AssistantViewFactory(libraryManager, prefs);
        settingsViewFactory = new SettingsViewFactory(prefs, libraryManager, this);

        setupNavigationBar();
        setupFilterBar(); // Initialize filter controls

        switchPage(currentPageId); // Load initial page
    }

    // Setup Navigation Bar (no changes needed here)
    /* public */ void setupNavigationBar() {
        navigationBox.getChildren().clear();
        for (PageInfo page : pages) {
            Button navButton = createNavButton(page);
            navigationBox.getChildren().add(navButton);
            if (page.getId().equals(currentPageId)) {
                currentNavButton = navButton;
                Platform.runLater(() -> {
                    if (navButton.getScene() != null) {
                         navButton.getStyleClass().add("active");
                    } else {
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
        Node iconNode = IconFactory.createIcon(page.getIconName(), IconFactory.NAV_ICON_SIZE);
        Button button = new Button(page.getTitle());
        button.setGraphic(iconNode);
        button.setContentDisplay(ContentDisplay.TOP);
        button.setUserData(page.getId());
        button.setOnAction(this::handleNavigation);
        button.getStyleClass().add("navbar-item");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    // --- Filter Bar Setup ---
    private void setupFilterBar() {
        // Ensure filterBar is not null (will be injected from FXML)
        if (filterBar == null || genreFilterComboBox == null || minPlaytimeFilterField == null || maxPlaytimeFilterField == null || clearFiltersButton == null) {
             System.err.println("Filter bar controls not injected correctly from FXML!");
             // Optionally hide the bar or disable functionality
             if(filterBar != null) filterBar.setVisible(false);
             return;
        }

        // Initially hide the filter bar, show it only for relevant pages
        filterBar.setVisible(false);
        filterBar.setManaged(false); // Don't reserve space when hidden

        // Populate Genre ComboBox
        ObservableList<String> availableGenres = FXCollections.observableArrayList(
                GameListViewFactory.AVAILABLE_GENRES.stream().sorted().collect(Collectors.toList())
        );
        genreFilterComboBox.getItems().addAll(availableGenres);
        genreFilterComboBox.setTitle("Filter by Genre"); // Placeholder text

        // Listener for Genre Changes
        genreFilterComboBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<String>) c -> {
            selectedGenres = new HashSet<>(genreFilterComboBox.getCheckModel().getCheckedItems());
            System.out.println("Selected Genres: " + selectedGenres); // Debug
            applyFilters();
        });

        // Listener for Playtime Changes (on text change or focus lost)
        minPlaytimeFilterField.textProperty().addListener((obs, oldVal, newVal) -> handlePlaytimeFilterChange());
        maxPlaytimeFilterField.textProperty().addListener((obs, oldVal, newVal) -> handlePlaytimeFilterChange());

        // Add numeric input validation (optional but recommended)
        addNumericValidation(minPlaytimeFilterField);
        addNumericValidation(maxPlaytimeFilterField);

        // Clear Filters Button Action
        clearFiltersButton.setOnAction(event -> clearFilters());
    }

    // Helper for numeric validation
    private void addNumericValidation(TextField textField) {
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) { // Allow only digits
                textField.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
    }

    // Handle changes in playtime fields
    private void handlePlaytimeFilterChange() {
        minPlaytime = parsePlaytimeInput(minPlaytimeFilterField.getText());
        maxPlaytime = parsePlaytimeInput(maxPlaytimeFilterField.getText());
        System.out.println("Playtime Filter: min=" + minPlaytime + ", max=" + maxPlaytime); // Debug
        applyFilters();
    }

    // Helper to parse playtime input safely
    private Integer parsePlaytimeInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(input.trim());
            return value >= 0 ? value : null; // Ensure non-negative
        } catch (NumberFormatException e) {
            return null; // Invalid input
        }
    }

    // Apply filters and refresh relevant views
    private void applyFilters() {
        // Refresh only if the current page uses the game list view
        if ("top_games".equals(currentPageId) || "new_games".equals(currentPageId)) {
            refreshCurrentPageIf(currentPageId);
        }
        // Update clear button state
        updateClearButtonState();
    }

    // Clear all filters
    private void clearFilters() {
        genreFilterComboBox.getCheckModel().clearChecks(); // Clears selection and triggers listener
        minPlaytimeFilterField.clear(); // Triggers listener
        maxPlaytimeFilterField.clear(); // Triggers listener
        // applyFilters() will be called by the listeners, no need to call it explicitly here
        // unless listeners weren't triggered (e.g., already empty)
        if (selectedGenres.isEmpty() && minPlaytime == null && maxPlaytime == null) {
             updateClearButtonState(); // Ensure button state is correct if nothing changed
        }
    }

    // Enable/disable clear button based on filter state
    private void updateClearButtonState() {
         boolean filtersActive = !selectedGenres.isEmpty() || minPlaytime != null || maxPlaytime != null;
         clearFiltersButton.setDisable(!filtersActive);
    }


    // --- Navigation ---
    @FXML
    private void handleNavigation(ActionEvent event) {
        if (event.getSource() instanceof Button) {
            Button clickedButton = (Button) event.getSource();
            String targetPageId = (String) clickedButton.getUserData();
            if (targetPageId != null && !targetPageId.equals(currentPageId) && !clickedButton.isDisabled()) {
                if (currentNavButton != null) {
                    currentNavButton.getStyleClass().remove("active");
                }
                clickedButton.getStyleClass().add("active");
                currentNavButton = clickedButton;
                switchPage(targetPageId);
            }
        }
    }

    // --- Page Switching ---
    public void switchPage(String pageId) {
        System.out.println("Switching to page: " + pageId);
        this.currentPageId = pageId;

        Optional<PageInfo> pageInfo = pages.stream().filter(p -> p.getId().equals(pageId)).findFirst();
        titleLabel.setText(pageInfo.map(PageInfo::getTitle).orElse("Unknown Page"));

        // Update Navigation Button States
        for (Node node : navigationBox.getChildren()) {
            if (node instanceof Button) {
                Button navButton = (Button) node;
                String buttonPageId = (String) navButton.getUserData();
                if (buttonPageId != null) {
                    navButton.setDisable(buttonPageId.equals(this.currentPageId));
                }
            }
        }

        // --- Show/Hide Filter Bar ---
        boolean showFilters = "top_games".equals(pageId) || "new_games".equals(pageId);
        if (filterBar != null) {
            filterBar.setVisible(showFilters);
            filterBar.setManaged(showFilters); // Manage layout space only when visible
            updateClearButtonState(); // Update button state when showing/hiding
        }

        // --- Content Loading ---
        contentPane.getChildren().clear();
        ProgressIndicator loadingIndicator = new ProgressIndicator(-1.0);
        loadingIndicator.setMaxSize(50, 50);
        contentPane.getChildren().add(loadingIndicator);
        StackPane.setAlignment(loadingIndicator, Pos.CENTER);

        Task<Parent> loadTask = new Task<>() {
            @Override
            protected Parent call() throws Exception {
                switch (pageId) {
                    case "top_games": {
                        String topJson = APIClient.getTopGames().join();
                        // Pass current filter state to the factory method
                        return gameListViewFactory.createGameListView(topJson, selectedGenres, minPlaytime, maxPlaytime);
                    }
                    case "new_games": {
                        String newJson = APIClient.getNewGames().join();
                         // Pass current filter state to the factory method
                        return gameListViewFactory.createGameListView(newJson, selectedGenres, minPlaytime, maxPlaytime);
                    }
                    case "search":
                        return searchViewFactory.createSearchView();
                    case "assistant":
                        return assistantViewFactory.createAssistantView();
                    case "library":
                        return libraryViewFactory.createLibraryView();
                    case "settings":
                        return settingsViewFactory.createSettingsView();
                    default:
                        return new VBox(new Label("Content for " + pageId + " not implemented."));
                }
            }
        };

        loadTask.setOnSucceeded(event -> {
            Parent pageContent = loadTask.getValue();
            contentPane.getChildren().clear();
            if (pageContent != null) {
                contentPane.getChildren().add(pageContent);
                StackPane.setAlignment(pageContent, Pos.TOP_LEFT);
            } else {
                 Label errorLabel = new Label("Error: Failed to load page content for " + pageId);
                 errorLabel.setStyle("-fx-text-fill: red;");
                 contentPane.getChildren().add(errorLabel);
                 StackPane.setAlignment(errorLabel, Pos.CENTER);
            }
        });

        loadTask.setOnFailed(event -> {
            contentPane.getChildren().clear();
            Throwable ex = loadTask.getException();
            ex.printStackTrace();
            Label errorLabel = new Label("Error loading page: " + pageId + "\n" + ex.getMessage());
            errorLabel.setWrapText(true);
            errorLabel.setStyle("-fx-text-fill: red;");
            contentPane.getChildren().add(errorLabel);
            StackPane.setAlignment(errorLabel, Pos.CENTER);
        });

        new Thread(loadTask).start();
    }


    // --- Library Interaction Methods (Called by GameItemNodeFactory) ---
    public void handleLibraryToggle(int gameId, JsonObject gameData) {
        boolean wasInLibrary = libraryManager.isInLibrary(gameId);
        boolean changed;
        if (wasInLibrary) {
            changed = libraryManager.removeLibraryItem(gameId);
        } else {
            changed = libraryManager.addLibraryItem(gameId, gameData);
        }
        if (changed) {
             refreshCurrentPageIf("library");
             refreshCurrentPageIf("assistant");
        }
    }


    // --- Share Action Method (Called by GameItemNodeFactory) ---
    public void handleShareGame(JsonObject game) {
        if (game == null) return;

        String gameName = "this game";
         if (game.has("name") && game.get("name").isJsonPrimitive()) {
             gameName = game.get("name").getAsString();
         }

        String gameSlugOrId = "unknown";
        if (game.has("slug") && game.get("slug").isJsonPrimitive()) {
            gameSlugOrId = game.get("slug").getAsString();
        } else if (game.has("id") && game.get("id").isJsonPrimitive()) {
            try {
                gameSlugOrId = game.get("id").getAsString();
            } catch (UnsupportedOperationException | NumberFormatException e) { /* ignore */ }
        }

        String gameUrl = "https://rawg.io/games/" + gameSlugOrId;
        if (gameSlugOrId.equals("unknown")) {
            gameUrl = "[Link not available]";
        }

        String shareText = "Check out this game: " + gameName + "\n" + gameUrl;
        System.out.println("Sharing: " + shareText);

        try {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(shareText);
            clipboard.setContent(content);
            DialogUtils.showInfoDialog("Shared!", gameName + " link copied to clipboard.");
        } catch (Exception e) {
            System.err.println("Could not copy to clipboard: " + e.getMessage());
            DialogUtils.showErrorDialog("Share Error", "Could not copy link to clipboard.");
        }
    }

    /**
     * Refreshes the content of the current page if its ID matches the given pageId.
     * @param pageIdToRefresh The ID of the page to potentially refresh.
     */
    public void refreshCurrentPageIf(String pageIdToRefresh) {
        if (pageIdToRefresh != null && pageIdToRefresh.equals(this.currentPageId)) {
            System.out.println("Refreshing current page due to filter change or external event: " + pageIdToRefresh);
            // Ensure the refresh logic also correctly sets the disabled state and filter bar visibility after reload
            Platform.runLater(() -> switchPage(this.currentPageId));
        }
    }
}
