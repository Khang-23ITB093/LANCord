module edu.vku.lancord {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.desktop;
    requires atlantafx.base;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    opens edu.vku.lancord to javafx.fxml;
    exports edu.vku.lancord;
    
    opens edu.vku.lancord.client.ui to javafx.fxml;
    exports edu.vku.lancord.client.ui;
    
    opens edu.vku.lancord.client to javafx.fxml;
    exports edu.vku.lancord.client;

    opens edu.vku.lancord.common.model to com.fasterxml.jackson.databind;
    exports edu.vku.lancord.common.model;
}