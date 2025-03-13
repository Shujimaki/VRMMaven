module com.example.vrmmaven {
    requires javafx.controls;
    requires javafx.fxml;
    requires google.api.services.sheets.v4.rev614;
    requires com.google.api.client.auth;
    requires com.google.api.client;
    requires com.google.api.client.json.gson;
    requires google.api.client;
    requires com.google.api.client.extensions.java6.auth;
    requires com.google.api.client.extensions.jetty.auth;
    requires jdk.httpserver;



    opens com.example.vrmmaven to javafx.fxml;
    exports com.example.vrmmaven;
}