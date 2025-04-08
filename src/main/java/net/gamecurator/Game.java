package net.gamecurator;

import java.util.List;
import java.util.StringJoiner;

public class Game {
    private String title;
    private List<String> genres;
    private int playDurationHours;

    public Game(String title, List<String> genres, int playDurationHours) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Game title cannot be empty.");
        }
        if (genres == null || genres.isEmpty()) {
            throw new IllegalArgumentException("Game must have at least one genre.");
        }
        if (playDurationHours < 0) {
            throw new IllegalArgumentException("Play duration cannot be negative.");
        }

        this.title = title;
        this.genres = genres;
        this.playDurationHours = playDurationHours;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getGenres() {
        return List.copyOf(genres);
    }

    public int getPlayDurationHours() {
        return playDurationHours;
    }

    @Override
    public String toString() {
        // Display game info easily
        StringJoiner genreJoiner = new StringJoiner(", ");
        for (String genre : genres) {
            genreJoiner.add(genre);
        }
        return "Game{" +
                "title='" + title + '\'' +
                ", genres=[" + genreJoiner + ']' +
                ", playDurationHours=" + playDurationHours +
                '}';
    }
}