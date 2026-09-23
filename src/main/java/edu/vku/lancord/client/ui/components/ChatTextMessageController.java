package edu.vku.lancord.client.ui.components;

import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatTextMessageController {

    @FXML private HBox messageRow;
    @FXML private Circle avatarCircle;
    @FXML private Label avatarInitialLabel;
    @FXML private Label senderLabel;
    @FXML private Label timestampLabel;
    @FXML private Label contentLabel;

    private static final String[] AVATAR_COLORS = {
        "#5865F2", "#23A559", "#E91E63", "#FF9800",
        "#9C27B0", "#00BCD4", "#FAA61A", "#3BA55C"
    };

    public void setData(String senderName, String content, Date timestamp) {
        senderLabel.setText(senderName);
        contentLabel.setText(content);

        if (timestamp != null) {
            String formatted = new SimpleDateFormat("'Today at' HH:mm").format(timestamp);
            timestampLabel.setText(formatted);
        }

        // Color avatar + initial by sender name hash for consistent identity
        int colorIdx = Math.abs(senderName.hashCode()) % AVATAR_COLORS.length;
        avatarCircle.setFill(Color.web(AVATAR_COLORS[colorIdx]));
        avatarInitialLabel.setText(senderName.substring(0, 1).toUpperCase());

        // Subtle fade-in animation on appear
        if (messageRow != null) {
            messageRow.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(150), messageRow);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
        }
    }
}
