package net.gamecurator;

import java.util.List;
// Concrete filter for game genres
public class GenreFilter extends FilterCriterion {
    private final List<String> targetGenres;

    public GenreFilter(List<String> targetGenres) {
        if (targetGenres == null || targetGenres.isEmpty()) {
            throw new IllegalArgumentException("Target genres cannot be null or empty.");
        }
        // Store genres consistently with lowercase for reliable comparison
        this.targetGenres = targetGenres.stream().map(String::toLowerCase).toList();
    }

    @Override
    public boolean matches(Game game) {
        if (game == null) return false;
        // Check if any of the game's genres match any of the target genres
        for (String gameGenre : game.getGenres()) {
            if (targetGenres.contains(gameGenre.toLowerCase())) {
                return true; // Found a match
            }
        }
        return false; // No match found
    }
}