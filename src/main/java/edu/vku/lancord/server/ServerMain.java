package edu.vku.lancord.server;

import edu.vku.lancord.server.core.ClientHandler;
import edu.vku.lancord.server.db.DatabaseManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMain {
    private static final int PORT = 8888;
    // Thread pool to handle up to 50 concurrent client connections
    private static final ExecutorService pool = Executors.newFixedThreadPool(50);

    public static void main(String[] args) {
        System.out.println("Starting LANCord Server...");
        
        try {
            // Initialize database connection
            DatabaseManager.getConnection();
            // Ensure server storage directory exists
            java.nio.file.Path storageDir = java.nio.file.Paths.get("server_storage");
            if (!java.nio.file.Files.exists(storageDir)) {
                java.nio.file.Files.createDirectories(storageDir);
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize server (DB or storage): " + e.getMessage());
            e.printStackTrace();
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server listening on port " + PORT);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler handler = new ClientHandler(clientSocket);
                pool.execute(handler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
