package edu.vku.lancord.client.ui.components;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Registry for file download callbacks keyed by fileId.
 * When a DOWNLOAD_FILE_RESP arrives, MainController dispatches bytes here.
 */
public class FileDownloadRegistry {

    private static final Map<Integer, Consumer<byte[]>> pendingCallbacks = new ConcurrentHashMap<>();

    public static void register(int fileId, Consumer<byte[]> callback) {
        pendingCallbacks.put(fileId, callback);
    }

    /** Returns and removes the callback for this fileId, or null if none registered. */
    public static Consumer<byte[]> consume(int fileId) {
        return pendingCallbacks.remove(fileId);
    }
}
