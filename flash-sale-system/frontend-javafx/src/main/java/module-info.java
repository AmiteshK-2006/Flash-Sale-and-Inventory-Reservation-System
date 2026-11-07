module com.flashsale.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;

    opens com.flashsale.client.controllers to javafx.fxml;
    exports com.flashsale.client;
}
