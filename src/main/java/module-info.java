module edu.vku.lancord {
    // Core Java modules
    requires java.sql;
    requires java.desktop;

    // JavaFX modules - explicit to prevent visibility errors
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires javafx.swing;

    // Third-party
    requires atlantafx.base;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires jbcrypt;
    requires webcam.capture;

    // ─── Client packages ───────────────────────────────────────────────────────
    exports edu.vku.lancord.client;
    opens  edu.vku.lancord.client to javafx.fxml;

    exports edu.vku.lancord.client.ui;
    opens  edu.vku.lancord.client.ui to javafx.fxml;

    exports edu.vku.lancord.client.ui.components;
    opens  edu.vku.lancord.client.ui.components to javafx.fxml;

    exports edu.vku.lancord.client.network;
    opens  edu.vku.lancord.client.network to javafx.fxml;

    // ─── Server packages ───────────────────────────────────────────────────────
    exports edu.vku.lancord.server;
    exports edu.vku.lancord.server.core;
    exports edu.vku.lancord.server.db;

    // ─── Common packages ───────────────────────────────────────────────────────
    exports edu.vku.lancord.common.model;
    opens  edu.vku.lancord.common.model to com.fasterxml.jackson.databind;

    exports edu.vku.lancord.common.protocol;
    opens  edu.vku.lancord.common.protocol to com.fasterxml.jackson.databind;
}