module org.alexw.gamecurator {
    requires transitive javafx.fxml; 
    requires jdk.compiler;          
    requires java.net.http;
    requires com.google.gson;        
    requires java.prefs;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign;
    requires javafx.controls;
    requires org.controlsfx.controls; 

    opens org.alexw.gamecurator to javafx.fxml, com.google.gson;
    opens org.alexw.gamecurator.misc to com.google.gson, javafx.fxml;
    opens org.alexw.gamecurator.ai to com.google.gson; 

    exports org.alexw.gamecurator;
    exports org.alexw.gamecurator.misc;
}