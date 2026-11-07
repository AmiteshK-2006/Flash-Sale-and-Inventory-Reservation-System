import os

base_pkg = "/Users/amitesh/Documents/antigravity/optimistic-pascal/flash-sale-system/frontend-javafx/src/main/java/com/flashsale/client"
res_pkg = "/Users/amitesh/Documents/antigravity/optimistic-pascal/flash-sale-system/frontend-javafx/src/main/resources/com/flashsale/client"

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(content)

java_files = {
    "MainApp.java": """package com.flashsale.client;

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
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
""",
    "controllers/MainController.java": """package com.flashsale.client.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import javafx.application.Platform;

public class MainController {
    @FXML
    private Label statusLabel;

    @FXML
    public void initialize() {
        statusLabel.setText("Connecting to backend...");
        fetchHealth();
    }

    private void fetchHealth() {
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/actuator/health"))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        statusLabel.setText("Backend is running: " + response.body());
                    } else {
                        statusLabel.setText("Backend returned " + response.statusCode());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error connecting to backend: " + e.getMessage()));
            }
        }).start();
    }
}
"""
}

res_files = {
    "views/MainView.fxml": """<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.VBox?>
<?import javafx.scene.control.Label?>
<?import javafx.geometry.Insets?>

<VBox alignment="CENTER" spacing="20.0" xmlns="http://javafx.com/javafx/8.0.171" xmlns:fx="http://javafx.com/fxml/1" fx:controller="com.flashsale.client.controllers.MainController">
    <children>
        <Label text="Flash Sale Demo Application" style="-fx-font-size: 24px;" />
        <Label fx:id="statusLabel" text="Loading..." />
    </children>
    <padding>
        <Insets bottom="20.0" left="20.0" right="20.0" top="20.0" />
    </padding>
</VBox>
"""
}

for path, content in java_files.items():
    write_file(f"{base_pkg}/{path}", content)

for path, content in res_files.items():
    write_file(f"{res_pkg}/{path}", content)

# module-info.java
write_file("/Users/amitesh/Documents/antigravity/optimistic-pascal/flash-sale-system/frontend-javafx/src/main/java/module-info.java", """module com.flashsale.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;

    opens com.flashsale.client.controllers to javafx.fxml;
    exports com.flashsale.client;
}
""")
