package net.gamecurator;

// Abstract class defining the filter contract
public abstract class FilterCriterion {
    public abstract boolean matches(Game game);
}