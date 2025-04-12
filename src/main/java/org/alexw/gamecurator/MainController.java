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
import org.controlsfx.control.CheckComboBox;

import java.net.URL;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class MainController implements Initializable {

    @FXML private BorderPane rootPane;
    @FXML private VBox navigationBox;
    @FXML private Label titleLabel;
    @FXML private StackPane contentPane;
    @FXML private HBox filterBar;
    @FXML private CheckComboBox<String> genreFilterComboBox;
    @FXML private TextField minPlaytimeFilterField;
    @FXML private TextField maxPlaytimeFilterField;
    @FXML private Button clearFiltersButton;

    private String currentPageId = "top_games";
    private Button currentNavButton = null;
    final Preferences prefs = Preferences.userNodeForPackage(MainController.class);
    final Gson gson = new Gson();

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

    // Managers and Factories
    LibraryManager libraryManager;
    GameItemNodeFactory gameItemNodeFactory;
    GameListViewFactory gameListViewFactory; // Keep separate as it needs parameters
    // Use a map for factories implementing the ViewFactory interface
    private final Map<String, ViewFactory> viewFactories = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        libraryManager = new LibraryManager(prefs, gson);
        gameItemNodeFactory = new GameItemNodeFactory(libraryManager, this);
        gameListViewFactory = new GameListViewFactory(gameItemNodeFactory);

        // Instantiate and populate the map with factories implementing ViewFactory
        viewFactories.put("search", new SearchViewFactory(gameItemNodeFactory));
        viewFactories.put("library", new LibraryViewFactory(libraryManager, gameItemNodeFactory));
        viewFactories.put("assistant", new AssistantViewFactory(libraryManager, prefs));
        viewFactories.put("settings", new SettingsViewFactory(prefs, libraryManager, this));

        setupNavigationBar();
        setupFilterBar();

        switchPage(currentPageId);
    }

     void setupNavigationBar() {
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

    private void setupFilterBar() {

        if (filterBar == null || genreFilterComboBox == null || minPlaytimeFilterField == null || maxPlaytimeFilterField == null || clearFiltersButton == null) {
             System.err.println("Filter bar controls not injected correctly from FXML!");

             if(filterBar != null) filterBar.setVisible(false);
             return;
        }

        filterBar.setVisible(false);
        filterBar.setManaged(false);

        ObservableList<String> availableGenres = FXCollections.observableArrayList(
                GameListViewFactory.AVAILABLE_GENRES.stream().sorted().collect(Collectors.toList())
        );
        genreFilterComboBox.getItems().addAll(availableGenres);
        genreFilterComboBox.setTitle("Filter by Genre");

        genreFilterComboBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<String>) c -> {
            selectedGenres = new HashSet<>(genreFilterComboBox.getCheckModel().getCheckedItems());
            System.out.println("Selected Genres: " + selectedGenres);
            applyFilters();
        });

        minPlaytimeFilterField.textProperty().addListener((obs, oldVal, newVal) -> handlePlaytimeFilterChange());
        maxPlaytimeFilterField.textProperty().addListener((obs, oldVal, newVal) -> handlePlaytimeFilterChange());

        addNumericValidation(minPlaytimeFilterField);
        addNumericValidation(maxPlaytimeFilterField);

        clearFiltersButton.setOnAction(event -> clearFilters());
    }

    private void addNumericValidation(TextField textField) {
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                textField.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
    }

    private void handlePlaytimeFilterChange() {
        minPlaytime = parsePlaytimeInput(minPlaytimeFilterField.getText());
        maxPlaytime = parsePlaytimeInput(maxPlaytimeFilterField.getText());
        System.out.println("Playtime Filter: min=" + minPlaytime + ", max=" + maxPlaytime);
        applyFilters();
    }

    private Integer parsePlaytimeInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(input.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void applyFilters() {

        if ("top_games".equals(currentPageId) || "new_games".equals(currentPageId)) {
            refreshCurrentPageIf(currentPageId);
        }

        updateClearButtonState();
    }

    private void clearFilters() {
        genreFilterComboBox.getCheckModel().clearChecks();
        minPlaytimeFilterField.clear();
        maxPlaytimeFilterField.clear();

        // Re-apply filters (which will now be empty) to refresh the view
        applyFilters();
    }

    private void updateClearButtonState() {
         boolean filtersActive = !selectedGenres.isEmpty() || minPlaytime != null || maxPlaytime != null;
         clearFiltersButton.setDisable(!filtersActive);
    }

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

    public void switchPage(String pageId) {
        System.out.println("Switching to page: " + pageId);
        this.currentPageId = pageId;

        Optional<PageInfo> pageInfo = pages.stream().filter(p -> p.getId().equals(pageId)).findFirst();
        titleLabel.setText(pageInfo.map(PageInfo::getTitle).orElse("Unknown Page"));

        // Disable the currently active navigation button
        for (Node node : navigationBox.getChildren()) {
            if (node instanceof Button) {
                Button navButton = (Button) node;
                String buttonPageId = (String) navButton.getUserData();
                if (buttonPageId != null) {
                    navButton.setDisable(buttonPageId.equals(this.currentPageId));
                }
            }
        }

        // Show/hide filter bar based on page
        boolean showFilters = "top_games".equals(pageId) || "new_games".equals(pageId);
        if (filterBar != null) {
            filterBar.setVisible(showFilters);
            filterBar.setManaged(showFilters);
            updateClearButtonState();
        }

        // Show loading indicator
        contentPane.getChildren().clear();
        ProgressIndicator loadingIndicator = new ProgressIndicator(-1.0);
        loadingIndicator.setMaxSize(50, 50);
        contentPane.getChildren().add(loadingIndicator);
        StackPane.setAlignment(loadingIndicator, Pos.CENTER);

        // Task to load page content asynchronously
        Task<Parent> loadTask = new Task<>() {
            @Override
            protected Parent call() throws Exception {
                // Handle pages requiring specific data separately
                if ("top_games".equals(pageId)) {
                    String topJson = APIClient.getTopGames().join();
                    return gameListViewFactory.createGameListView(topJson, selectedGenres, minPlaytime, maxPlaytime);
                } else if ("new_games".equals(pageId)) {
                    String newJson = APIClient.getNewGames().join();
                    return gameListViewFactory.createGameListView(newJson, selectedGenres, minPlaytime, maxPlaytime);
                } else {
                    // Use the map for pages implementing ViewFactory
                    ViewFactory factory = viewFactories.get(pageId);
                    if (factory != null) {
                        return factory.createView();
                    } else {
                        // Fallback for unknown pages
                        System.err.println("No view factory found for page ID: " + pageId);
                        return new VBox(new Label("Content for " + pageId + " not implemented or factory missing."));
                    }
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

    // Called by GameItemNodeFactory when the library button is clicked
    public void handleLibraryToggle(int gameId, JsonObject gameData) {
        boolean wasInLibrary = libraryManager.isInLibrary(gameId);
        boolean changed;
        if (wasInLibrary) {
            changed = libraryManager.removeLibraryItem(gameId);
        } else {
            changed = libraryManager.addLibraryItem(gameId, gameData);
        }
        if (changed) {
             // Refresh library view if it's the current page
             refreshCurrentPageIf("library");
             // Refresh assistant view if it's the current page (as recommendations depend on library)
             refreshCurrentPageIf("assistant");
        }
    }

    // Called by GameItemNodeFactory when the share button is clicked
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

    // Refreshes the current page content if its ID matches the provided one.
    // Useful for updating views after data changes (e.g., library updates, filter changes).
    public void refreshCurrentPageIf(String pageIdToRefresh) {
        if (pageIdToRefresh != null && pageIdToRefresh.equals(this.currentPageId)) {
            System.out.println("Refreshing current page due to filter change or external event: " + pageIdToRefresh);

            // Re-run the switchPage logic for the current page to reload its content
            Platform.runLater(() -> switchPage(this.currentPageId));
        }
    }
}