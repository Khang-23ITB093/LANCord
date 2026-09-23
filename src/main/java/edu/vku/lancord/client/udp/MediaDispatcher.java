package edu.vku.lancord.client.udp;

@FunctionalInterface
public interface MediaDispatcher {
    void onMedia(byte senderId, byte mediaType, byte[] fullData);
}
