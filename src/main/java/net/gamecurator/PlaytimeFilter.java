package net.gamecurator;

// Concrete filter for game playtime
public class PlaytimeFilter extends FilterCriterion {
    private final int minHours;
    private final int maxHours;

    public PlaytimeFilter(int minHours, int maxHours) {
        if (minHours < 0 || maxHours < 0 || minHours > maxHours) {
            throw new IllegalArgumentException("Invalid playtime range provided.");
        }
        this.minHours = minHours;
        this.maxHours = maxHours;
    }

    @Override
    public boolean matches(Game game) {
        if (game == null) return false;
        int duration = game.getPlayDurationHours();
        return duration >= minHours && duration <= maxHours;
    }
}