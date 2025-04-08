package net.gamecurator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Library {
    private List<Game> ownedGames;

    public Library() {
        this.ownedGames = new ArrayList<>();
    }

    public void addGame(Game game) {
        if (game != null && ownedGames.stream().noneMatch(g -> g.getTitle().equalsIgnoreCase(game.getTitle()))) {
            this.ownedGames.add(game);
        } else if (game != null) {
            System.out.println("Warning: Game '" + game.getTitle() + "' already exists in the library.");
        }
    }

    public List<Game> getOwnedGames() {
        return List.copyOf(ownedGames);
    }

    public List<String> getAllGameDetails() {
        return ownedGames.stream()
                .map(game -> "Title: " + game.getTitle() + ", Genres: " + String.join(", ", game.getGenres()))
                .collect(Collectors.toList());
    }

    public List<Game> getFilteredGames(FilterCriterion criterion) {
        if (criterion == null) {
            return List.copyOf(ownedGames); // Return all games if no criterion
        }
        return ownedGames.stream()
                .filter(criterion::matches)
                .collect(Collectors.toList());
    }
}