package net.gamecurator;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // 1. Maak een gebruiker aan
        User user = new User("Gamer123");
        Library library = user.getLibrary();

        // 2. Voeg games toe aan de bibliotheek
        library.addGame(new Game("Cyberpunk 2077", List.of("RPG", "Action"), 80));
        library.addGame(new Game("The Witcher 3", List.of("RPG", "Fantasy"), 150));
        library.addGame(new Game("Portal 2", List.of("Puzzle", "Platformer"), 10));
        library.addGame(new Game("Doom Eternal", List.of("Shooter", "Action"), 25));
        library.addGame(new Game("Stardew Valley", List.of("RPG", "Simulation"), 200));
        library.addGame(new Game("Tetris Effect", List.of("Puzzle"), 5));
        library.addGame(new Game("Cyberpunk 2077", List.of("RPG", "Action"), 80)); // Probeer duplicate toe te voegen

        System.out.println("--- Alle Game Details (US1) ---");
        library.getAllGameDetails().forEach(System.out::println);
        System.out.println();

        // 3. Filter op Genre (US2) - RPG or Shooter
        System.out.println("--- Filter: RPG of Shooter (US2) ---");
        FilterCriterion genreFilter = new GenreFilter(List.of("RPG", "Shooter"));
        List<Game> rpgOrShooterGames = library.getFilteredGames(genreFilter);
        rpgOrShooterGames.forEach(System.out::println);
        System.out.println();

        // 4. Filter op Speelduur (US3) - Korte games (0-20 uur)
        System.out.println("--- Filter: Korte Games (0-20 uur) (US3) ---");
        FilterCriterion playtimeFilter = new PlaytimeFilter(0, 20);
        List<Game> shortGames = library.getFilteredGames(playtimeFilter);
        shortGames.forEach(System.out::println);
        System.out.println();

        // 5. Filter op Speelduur (US3) - Lange games (100+ uur)
        System.out.println("--- Filter: Lange Games (100+ uur) (US3) ---");
        FilterCriterion longPlaytimeFilter = new PlaytimeFilter(100, Integer.MAX_VALUE);
        List<Game> longGames = library.getFilteredGames(longPlaytimeFilter);
        longGames.forEach(System.out::println);
    }
}