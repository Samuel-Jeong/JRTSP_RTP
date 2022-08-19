package org.jmagni.jrtsp.rtsp.base;

import java.io.Serializable;
import java.nio.ByteBuffer;

public class RtpPacket implements Serializable {
    public static final int RTP_PACKET_MAX_SIZE = 8192;
    public static final int FIXED_HEADER_SIZE = 12;
    public static final int EXT_HEADER_SIZE = 4;
    public static final int VERSION = 2;
    private static final long serialVersionUID = -1590053946635208723L;
    private ByteBuffer buffer;

    public RtpPacket(int capacity, boolean allocateDirect) {
        this.buffer = allocateDirect ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
    }

    public RtpPacket(boolean allocateDirect) {
        this(8192, allocateDirect);
    }

    public ByteBuffer getBuffer() {
        return this.buffer;
    }

    public byte[] getRawData() {
        byte[] data = new byte[this.getLength()];
        this.buffer.rewind();
        this.buffer.get(data);
        return data;
    }

    public int getVersion() {
        return (this.buffer.get(0) & 192) >> 6;
    }

    public int getContributingSource() {
        return this.buffer.get(0) & 15;
    }

    public boolean hasPadding() {
        return (this.buffer.get(0) & 32) == 32;
    }

    public boolean hasExtensions() {
        return (this.buffer.get(0) & 16) == 16;
    }

    public boolean getMarker() {
        return (this.buffer.get(1) & 255 & 128) == 128;
    }

    public int getPayloadType() {
        return this.buffer.get(1) & 255 & 127;
    }

    public int getSeqNumber() {
        return this.buffer.getShort(2) & '\uffff';
    }

    public long getTimestamp() {
        return (long)(this.buffer.get(4) & 255) << 24 | (long)(this.buffer.get(5) & 255) << 16 | (long)(this.buffer.get(6) & 255) << 8 | (long)(this.buffer.get(7) & 255);
    }

    public long getSyncSource() {
        return this.readUnsignedIntAsLong(8);
    }

    public void setSyncSource(long ssrc) {
        byte[] data = this.getRawData();
        if (data != null && data.length >= 12) {
            setLong(ssrc, data, 8, 12);
            this.buffer = ByteBuffer.wrap(data);
        }

    }

    private static void setLong(long n, byte[] data, int begin, int end) {
        --end;

        while(end >= begin) {
            data[end] = (byte)((int)(n % 256L));
            n >>= 8;
            --end;
        }

    }

    public long GetRTCPSyncSource() {
        return this.readUnsignedIntAsLong(4);
    }

    public long readUnsignedIntAsLong(int off) {
        this.buffer.position(off);
        return ((long)(this.buffer.get() & 255) << 24 | (long)(this.buffer.get() & 255) << 16 | (long)(this.buffer.get() & 255) << 8 | (long)(this.buffer.get() & 255)) & 4294967295L;
    }

    public void getPayload(byte[] buff, int offset) {
        this.buffer.position(12);
        this.buffer.get(buff, offset, this.buffer.limit() - 12);
    }

    public void getPayload(byte[] buff) {
        this.getPayload(buff, 0);
    }

    public void wrap(byte[] data) {
        this.buffer.clear();
        this.buffer.put(data);
        this.buffer.flip();
    }

    public void wrap(boolean mark, int payloadType, int seqNumber, long timestamp, long ssrc, byte[] data, int offset, int len) {
        this.buffer.clear();
        this.buffer.rewind();
        this.buffer.put((byte)-128);
        byte b = (byte)payloadType;
        if (mark) {
            b = (byte)(b | 128);
        }

        this.buffer.put(b);
        this.buffer.put((byte)((seqNumber & '\uff00') >> 8));
        this.buffer.put((byte)(seqNumber & 255));
        this.buffer.put((byte)((int)((timestamp & -16777216L) >> 24)));
        this.buffer.put((byte)((int)((timestamp & 16711680L) >> 16)));
        this.buffer.put((byte)((int)((timestamp & 65280L) >> 8)));
        this.buffer.put((byte)((int)(timestamp & 255L)));
        this.buffer.put((byte)((int)((ssrc & -16777216L) >> 24)));
        this.buffer.put((byte)((int)((ssrc & 16711680L) >> 16)));
        this.buffer.put((byte)((int)((ssrc & 65280L) >> 8)));
        this.buffer.put((byte)((int)(ssrc & 255L)));
        this.buffer.put(data, offset, len);
        this.buffer.flip();
        this.buffer.rewind();
    }

    public String toString() {
        boolean var10000 = this.getMarker();
        return "RTP Packet[marker=" + var10000 + ", seq=" + this.getSeqNumber() + ", timestamp=" + this.getTimestamp() + ", payload_size=" + this.getPayloadLength() + ", payload=" + this.getPayloadType() + "]";
    }

    public void shrink(int delta) {
        if (delta > 0) {
            int newLimit = this.buffer.limit() - delta;
            if (newLimit <= 0) {
                newLimit = 0;
            }

            this.buffer.limit(newLimit);
        }
    }

    public int getHeaderLength() {
        return this.getExtensionBit() ? 12 + 4 * this.getCsrcCount() + 4 + this.getExtensionLength() : 12 + 4 * this.getCsrcCount();
    }

    public int getPayloadLength() {
        return this.buffer.limit() - this.getHeaderLength();
    }

    public int getExtensionLength() {
        if (!this.getExtensionBit()) {
            return 0;
        } else {
            int extLenIndex = 12 + this.getCsrcCount() * 4 + 2;
            return this.buffer.get(extLenIndex) << 8 | this.buffer.get(extLenIndex + 1) * 4;
        }
    }

    public boolean getExtensionBit() {
        this.buffer.rewind();
        return (this.buffer.get() & 16) == 16;
    }

    public int getCsrcCount() {
        this.buffer.rewind();
        return this.buffer.get() & 15;
    }

    public int getPaddingSize() {
        this.buffer.rewind();
        return (this.buffer.get() & 4) == 0 ? 0 : this.buffer.get(this.buffer.limit() - 1);
    }

    public int getLength() {
        return this.buffer.limit();
    }

    public int getOffset() {
        return this.buffer.position();
    }

    public void grow(int delta) {
        if (delta != 0) {
            int newLen = this.buffer.limit() + delta;
            if (newLen <= this.buffer.capacity()) {
                this.buffer.limit(newLen);
            } else {
                ByteBuffer newBuffer = this.buffer.isDirect() ? ByteBuffer.allocateDirect(newLen) : ByteBuffer.allocate(newLen);
                this.buffer.rewind();
                newBuffer.put(this.buffer);
                newBuffer.limit(newLen);
                this.buffer = newBuffer;
            }
        }
    }

    public void append(byte[] data, int len) {
        if (data != null && len > 0 && len <= data.length) {
            int oldLimit = this.buffer.limit();
            this.grow(len);
            this.buffer.position(oldLimit);
            this.buffer.limit(oldLimit + len);
            this.buffer.put(data, 0, len);
        } else {
            throw new IllegalArgumentException("Invalid combination of parameters data and length to append()");
        }
    }

    public void readRegionToBuff(int off, int len, byte[] outBuff) {
        assert off >= 0;

        assert len > 0;

        assert outBuff != null;

        assert outBuff.length >= len;

        assert this.buffer.limit() >= off + len;

        this.buffer.position(off);
        this.buffer.get(outBuff, 0, len);
    }
}
