package edu.vku.lancord.client.udp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class UDPHeader {
    public static final byte MEDIA_AUDIO  = 0x00;
    public static final byte MEDIA_WEBCAM = 0x01;
    public static final byte MEDIA_SCREEN = 0x02;

    public static final int HEADER_SIZE   = 12;

    public static final byte FLAG_IS_FRAGMENT      = 0x01;
    public static final byte FLAG_IS_LAST_FRAGMENT = 0x02;
    public static final byte FLAG_IS_KEYFRAME      = 0x04;

    public static class ParsedHeader {
        public byte mediaType;
        public byte flags;
        public short seqId;
        public short fragIndex;
        public short totalFrags;
        public short payloadLen;
        public byte senderId;
    }

    public static byte[] encode(
        byte mediaType, byte flags,
        short seqId, short fragIndex, short totalFrags,
        short payloadLen, byte senderId
    ) {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(mediaType);
        buf.put(flags);
        buf.putShort(seqId);
        buf.putShort(fragIndex);
        buf.putShort(totalFrags);
        buf.putShort(payloadLen);
        buf.put(senderId);
        buf.put((byte) 0x00);
        return buf.array();
    }

    public static ParsedHeader decode(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, 0, HEADER_SIZE);
        buf.order(ByteOrder.BIG_ENDIAN);
        ParsedHeader h = new ParsedHeader();
        h.mediaType      = buf.get();
        h.flags          = buf.get();
        h.seqId          = buf.getShort();
        h.fragIndex      = buf.getShort();
        h.totalFrags     = buf.getShort();
        h.payloadLen     = buf.getShort();
        h.senderId       = buf.get();
        buf.get(); // skip reserved
        return h;
    }
}
