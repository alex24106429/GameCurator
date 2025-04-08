package net.gamecurator;

public class User {
    private final String username;
    private final Library library;

    public User(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        this.username = username;
        this.library = new Library(); // Composition: User owns its Library
    }

    public String getUsername() {
        return username;
    }

    public Library getLibrary() {
        return library;
    }
}