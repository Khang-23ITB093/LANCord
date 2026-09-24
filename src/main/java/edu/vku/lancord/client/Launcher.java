package edu.vku.lancord.client;

/**
 * Non-JavaFX Application launcher class.
 * <p>
 * This class does NOT extend {@code javafx.application.Application},
 * allowing it to be packaged into a standalone fat/uber JAR and executed
 * directly via {@code java -jar} or double-clicked without requiring
 * JavaFX modules on the module path.
 */
public class Launcher {
    public static void main(String[] args) {
        if (args.length > 0 && "server".equalsIgnoreCase(args[0])) {
            edu.vku.lancord.server.ServerMain.main(args);
        } else {
            ClientMain.main(args);
        }
    }
}
