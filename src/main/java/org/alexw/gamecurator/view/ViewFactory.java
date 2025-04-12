package org.alexw.gamecurator.view;

import javafx.scene.Parent;

/**
 * Interface for factories that create main view components.
 */
public interface ViewFactory {
    /**
     * Creates and returns the main view component.
     * @return The root node of the created view.
     */
    Parent createView();
}