package edu.vku.lancord.client.ui.components;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatTextMessageController {

    @FXML private Circle avatarCircle;
    @FXML private Label senderLabel;
    @FXML private Label timestampLabel;
    @FXML private Label contentLabel;

    private static final String[] AVATAR_COLORS = {
        "#5865F2", "#57F287", "#FEE75C", "#EB459E", "#ED4245",
        "#23A559", "#3BA55C", "#FAA61A"
    };

    public void setData(String senderName, String content, Date timestamp) {
        senderLabel.setText(senderName);
        contentLabel.setText(content);

        if (timestamp != null) {
            timestampLabel.setText(new SimpleDateFormat("HH:mm").format(timestamp));
        }

        // Color avatar based on first char of name for visual identity
        int colorIdx = Math.abs(senderName.hashCode()) % AVATAR_COLORS.length;
        avatarCircle.setFill(Color.web(AVATAR_COLORS[colorIdx]));
    }
}
