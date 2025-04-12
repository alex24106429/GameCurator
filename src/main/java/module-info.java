module org.alexw.gamecurator {
    // Keep your existing requires directives
    requires transitive javafx.fxml; // Note: transitive is only needed if other modules depending on yours also need fxml directly.
    requires jdk.compiler;          // Be sure you actually need this at runtime.
    requires java.net.http;
    requires com.google.gson;        // Good, this is required
    requires java.prefs;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign;
    requires javafx.controls;
    requires org.controlsfx.controls; // Added ControlsFX module


    // Keep your existing opens directive for JavaFX FXML
    opens org.alexw.gamecurator to javafx.fxml, com.google.gson;
    opens org.alexw.gamecurator.misc to com.google.gson, javafx.fxml;
    opens org.alexw.gamecurator.ai to com.google.gson; // Added for Gson reflection

    // Keep your existing exports directive
    exports org.alexw.gamecurator;
    exports org.alexw.gamecurator.misc;
}