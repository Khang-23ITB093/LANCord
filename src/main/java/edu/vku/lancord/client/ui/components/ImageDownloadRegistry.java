package edu.vku.lancord.client.ui.components;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Registry for one-shot image download callbacks keyed by fileId.
 * When a DOWNLOAD_FILE_RESP arrives in MainController, it checks this registry
 * and dispatches image bytes to the waiting ImageMessage component.
 */
public class ImageDownloadRegistry {

    private static final Map<Integer, Consumer<byte[]>> pendingCallbacks = new ConcurrentHashMap<>();

    public static void register(int fileId, Consumer<byte[]> callback) {
        pendingCallbacks.put(fileId, callback);
    }

    /** Returns and removes the callback for this fileId, or null if none registered. */
    public static Consumer<byte[]> consume(int fileId) {
        return pendingCallbacks.remove(fileId);
    }

    public static boolean hasPending(int fileId) {
        return pendingCallbacks.containsKey(fileId);
    }
}
