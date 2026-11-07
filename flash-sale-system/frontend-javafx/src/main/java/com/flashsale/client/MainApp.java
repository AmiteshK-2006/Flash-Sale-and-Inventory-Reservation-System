package com.flashsale.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class MainApp extends Application {
    
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        setRoot("views/MainView");
    }

    public static void setRoot(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource(fxml + ".fxml"));
        Parent root = fxmlLoader.load();
        Scene scene = new Scene(root, 1020, 720);
        
        var css = MainApp.class.getResource("styles/style.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        
        primaryStage.setTitle("⚡ Flash Sale Engine - High-Throughput Inventory Reservation");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
