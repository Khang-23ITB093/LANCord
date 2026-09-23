package edu.vku.lancord.client.udp;

public class FragmentBuffer {
    public byte mediaType;
    public byte senderId;
    public short seqId;
    public short totalFrags;
    public byte[][] fragments;
    public int receivedCount;
    public long createdAt;

    public FragmentBuffer(UDPHeader.ParsedHeader h) {
        this.mediaType = h.mediaType;
        this.senderId = h.senderId;
        this.seqId = h.seqId;
        this.totalFrags = h.totalFrags;
        this.fragments = new byte[Short.toUnsignedInt(h.totalFrags)][];
        this.receivedCount = 0;
        this.createdAt = System.currentTimeMillis();
    }
}
