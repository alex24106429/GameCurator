package org.alexw.gamecurator;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {

    public static FXMLLoader switchView(String fxmlName) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource(fxmlName));
        Parent root = fxmlLoader.load();

        Scene scene = new Scene(root, 800, 600);

        globalStage.setTitle("GameCurator");
        globalStage.setScene(scene);
        globalStage.show();

        return fxmlLoader;
    }

    public static Stage globalStage;

    @Override
    public void start(Stage stage) throws IOException {
        globalStage = stage;

        globalStage.setMinWidth(640);
        globalStage.setMinHeight(400);

        switchView("MainView.fxml");
    }

    public static void main(String[] args) {
        launch();
    }
}