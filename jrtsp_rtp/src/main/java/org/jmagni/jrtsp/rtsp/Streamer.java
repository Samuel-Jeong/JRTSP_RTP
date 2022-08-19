package org.jmagni.jrtsp.rtsp;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.rtsp.RtspHeaderNames;
import io.netty.handler.codec.rtsp.RtspHeaderValues;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ntp.TimeStamp;
import org.jmagni.jrtsp.config.UserConfig;
import org.jmagni.jrtsp.rtsp.base.MediaType;
import org.jmagni.jrtsp.rtsp.base.RtpPacket;
import org.jmagni.jrtsp.rtsp.netty.handler.StreamerChannelHandler;
import org.jmagni.jrtsp.rtsp.rtcp.type.regular.RtcpSenderReport;
import org.jmagni.jrtsp.rtsp.rtcp.type.regular.base.report.RtcpReportBlock;
import org.jmagni.jrtsp.service.AppInstance;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class Streamer {

    private final MediaType mediaType;
    private final String callId;
    private final String sessionId;

    private final boolean isTcp;
    private String clientUserAgent = null;

    private NioEventLoopGroup group = null;
    private final Bootstrap b = new Bootstrap();

    private double startTime = 0.0d;
    private double endTime = 0.0d;

    private final String listenIp;
    private final int listenPort;

    private String uri = null;
    private int congestionLevel = 0;

    private Channel rtpDestChannel = null; /* 메시지 송신용 채널 */
    InetSocketAddress rtpTargetAddress = null;
    private Channel rtcpDestChannel = null; /* 메시지 송신용 채널 */
    InetSocketAddress rtcpTargetAddress = null;

    private String destIp = null;
    private int rtpDestPort = 0; // rtp destination port
    private int rtcpDestPort = 0; // rtcp destination port

    private final String trackId;
    public static final String TRACK_ID_TAG = "trackID";
    public static final String AUDIO_TRACK_ID = "1";
    private long audioSsrc;
    private final AtomicInteger audioCurSeqNum = new AtomicInteger(0);
    private final AtomicLong audioCurTimeStamp = new AtomicLong(0);

    public static final String VIDEO_TRACK_ID = "2";
    private long videoSsrc;
    private final AtomicInteger videoCurSeqNum = new AtomicInteger(0);
    private final AtomicLong videoCurTimeStamp = new AtomicLong(0);

    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    private final int RTCP_SR_LIMIT_COUNT = 5;
    private int curRtcpSrCount = 5;
    private int spc = 0;

    private ChannelHandlerContext channelContext = null;

    private DefaultFullHttpResponse playResponse = null;
    private final ReentrantLock playResponseLock = new ReentrantLock();

    private final byte TCP_RTP_MAGIC_NUMBER = 0X24;

    public Streamer(MediaType mediaType, String callId, String sessionId, String trackId, boolean isTcp, String listenIp, int listenPort) {
        this.mediaType = mediaType;
        this.callId = callId;
        this.sessionId = sessionId;
        this.trackId = trackId;
        this.isTcp = isTcp;
        this.listenIp = listenIp;
        this.listenPort = listenPort;

        log.debug("({}) Streamer({}) is created. (callId={}, trackId={}, listenIp={}, listenPort={})",
                sessionId, mediaType.getName(), callId, trackId, listenIp, listenPort
        );
        log.warn("({}) Streamer's transport is [{}]", getKey(), isTcp? "TCP" : "UDP");
    }

    public Streamer init() {
        UserConfig userConfig = AppInstance.getInstance().getConfigManager().getUserConfig();
        group = new NioEventLoopGroup(userConfig.getStreamThreadPoolSize());
        b.group(group).channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, false)
                .option(ChannelOption.SO_SNDBUF, userConfig.getSendBufSize())
                .option(ChannelOption.SO_RCVBUF, userConfig.getRecvBufSize())
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    public void initChannel (final NioDatagramChannel ch) {
                        final ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(
                                //new DefaultEventExecutorGroup(1),
                                new StreamerChannelHandler(callId)
                        );
                    }
                });
        return this;
    }

    public void open() {
        try {
            if (!isTcp) {
                if (rtpDestPort > 0) {
                    InetAddress address = InetAddress.getByName(destIp);
                    ChannelFuture rtpChannelFuture = b.connect(address, rtpDestPort).sync();
                    rtpDestChannel = rtpChannelFuture.channel();
                    rtpTargetAddress = new InetSocketAddress(destIp, rtpDestPort);

                    if (rtcpDestPort > 0) {
                        ChannelFuture rtcpChannelFuture = b.connect(address, rtcpDestPort).sync();
                        rtcpDestChannel = rtcpChannelFuture.channel();
                        rtcpTargetAddress = new InetSocketAddress(destIp, rtcpDestPort);
                    }
                    log.debug("({}) UDP Streamer is opened. (destIp={}, rtpDestPort={}, rtcpDestPort={})",
                            getKey(), destIp, rtpDestPort, rtcpDestPort
                    );
                } else {
                    log.warn("({}) UDP Streamer is not opened. (destIp={}, rtpDestPort={})", getKey(), destIp, rtpDestPort);
                }
            }
        } catch (Exception e) {
            log.warn("({}) Streamer.start.Exception", getKey(), e);
        }
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public String getCallId() {
        return callId;
    }

    public boolean isStarted() {
        return isStarted.get();
    }

    public void setStarted(boolean isPaused) {
        this.isStarted.set(isPaused);
    }

    public void close () {
        closeRtpTarget();
        closeRtcpTarget();
    }

    private void closeRtpTarget() {
        if (rtpDestChannel != null) {
            rtpDestChannel.closeFuture();
            rtpDestChannel.close();
            rtpDestChannel = null;
        }
    }

    private void closeRtcpTarget() {
        if (rtcpDestChannel != null) {
            rtcpDestChannel.closeFuture();
            rtcpDestChannel.close();
            rtcpDestChannel = null;
        }
    }

    public void start() {
        isStarted.set(true);
        //log.debug("({}) Streamer is started. ({})", getKey(), this);
    }

    public void stop () {
        close();
        isStarted.set(false);
        //log.debug("({}) Streamer is stopped. ({})", getKey(), this);
    }

    public void finish () {
        stop();

        if (group != null) {
            group.shutdownGracefully();
        }

        log.debug("({}) Streamer is finished.", getKey());
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getListenIp() {
        return listenIp;
    }

    public int getListenPort() {
        return listenPort;
    }

    public Channel getRtpDestChannel() {
        return rtpDestChannel;
    }

    public String getDestIp() {
        return destIp;
    }

    public int getRtpDestPort() {
        return rtpDestPort;
    }

    public int getRtcpDestPort() {
        return rtcpDestPort;
    }

    public long getAudioSsrc() {
        return audioSsrc;
    }

    public void setAudioSsrc(long audioSsrc) {
        this.audioSsrc = audioSsrc;
    }

    public long getVideoSsrc() {
        return videoSsrc;
    }

    public void setVideoSsrc(long videoSsrc) {
        this.videoSsrc = videoSsrc;
    }

    public void setDestIp(String destIp) {
        this.destIp = destIp;
    }

    public void setRtpDestPort(int rtpDestPort) {
        this.rtpDestPort = rtpDestPort;
    }

    public void setRtcpDestPort(int rtcpDestPort) {
        this.rtcpDestPort = rtcpDestPort;
    }

    public void setVideoCurSeqNum(int videoCurSeqNum) {
        this.videoCurSeqNum.set(videoCurSeqNum);
    }

    public int getVideoCurSeqNum() {
        return videoCurSeqNum.get();
    }

    public void setVideoCurTimeStamp(long videoCurTimeStamp) {
        this.videoCurTimeStamp.set(videoCurTimeStamp);
    }

    public long getVideoCurTimeStamp() {
        return videoCurTimeStamp.get();
    }

    public int getAudioCurSeqNum() {
        return audioCurSeqNum.get();
    }

    public void setAudioCurSeqNum(int audioCurSeqNum) {
        this.audioCurSeqNum.set(audioCurSeqNum);
    }

    public long getAudioCurTimeStamp() {
        return audioCurTimeStamp.get();
    }

    public void setAudioCurTimeStamp(long audioCurTimeStamp) {
        this.audioCurTimeStamp.set(audioCurTimeStamp);
    }

    public String getClientUserAgent() {
        return clientUserAgent;
    }

    public void setClientUserAgent(String clientUserAgent) {
        this.clientUserAgent = clientUserAgent;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public boolean isRtpDestActive() {
        if (rtpDestChannel != null) {
            return rtpDestChannel.isActive() && rtpDestChannel.isOpen();
        } else {
            return false;
        }
    }

    public boolean isRtcpDestActive() {
        if (rtcpDestChannel != null) {
            return rtcpDestChannel.isActive() && rtcpDestChannel.isOpen();
        } else {
            return false;
        }
    }

    public double getStartTime() {
        return startTime;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
    }

    public double getEndTime() {
        return endTime;
    }

    public void setEndTime(double endTime) {
        this.endTime = endTime;
    }

    public void setChannelContext(ChannelHandlerContext channelContext) {
        this.channelContext = channelContext;
    }

    public boolean isTcp() {
        return isTcp;
    }

    public void sendPlayResponse() {
        if (channelContext == null) {
            log.warn("({}) Fail to send the play response. Context is null.", getKey());
            return;
        } else if (playResponse == null) {
            log.warn("({}) Fail to send the play response. Response is null.", getKey());
            return;
        }

        playResponseLock.lock();
        try {
            playResponse.headers().add(
                    RtspHeaderNames.RTP_INFO,
                    RtspHeaderValues.URL + "=" + uri + "/" + TRACK_ID_TAG + "=" + AUDIO_TRACK_ID
                            + ";" + RtspHeaderValues.SEQ + "=" + getAudioCurSeqNum()
                            + ";" + RtspHeaderValues.RTPTIME + "=" + getAudioCurTimeStamp() + "," +
                            RtspHeaderValues.URL + "=" + uri + "/" + TRACK_ID_TAG + "=" + VIDEO_TRACK_ID
                            + ";" + RtspHeaderValues.SEQ + "=" + getVideoCurSeqNum()
                            + ";" + RtspHeaderValues.RTPTIME + "=" + getVideoCurTimeStamp()
            );
            channelContext.writeAndFlush(playResponse);
            log.debug("({}) [PLAY] > Success to send the response: {}\n", getKey(), playResponse);

            playResponse = null;
        } catch (Exception e) {
            // ignore
        } finally {
            playResponseLock.unlock();
        }
    }

    public void sendRtpPacket(RtpPacket rtpPacket, String mediaType) {
        if (isTcp()) {
            sendRtpPacketWithTcp(rtpPacket);
        } else {
            if (this.mediaType.getName().equals(mediaType)) {
                sendRtpPacketWithUdp(rtpPacket);
            }
        }
    }

    public void sendRtpPacketWithTcp(RtpPacket rtpPacket) {
        if (channelContext == null) { return; }

        /**
         * The RTP data will be encapsulated in the following format:
         *    | magic number | channel number | Embedded data length | data |
         *
         *    1. Magic Number - 1 byte value of hex 0X24 (RTP data identifier)
         *    2. Channel number - 1 byte value to denote the channel number-1 bytes used to indicate the channel
         *    3. The embedded date length - 2 bytes to indicate the length of the inserted data
         *    4. RTP data (byte array)
         */

        byte[] rtpPacketRawData = rtpPacket.getRawData();
        int rtpDataLength = rtpPacketRawData.length;

        byte[] newRtpData = new byte[1 + 1 + 2 + rtpDataLength];
        newRtpData[0] = TCP_RTP_MAGIC_NUMBER;
        newRtpData[1] = Byte.parseByte(trackId);

        byte[] rtpDataLengthArray = ByteBuffer.allocate(2).putShort((short) rtpDataLength).array();
        newRtpData[2] = rtpDataLengthArray[0];
        newRtpData[3] = rtpDataLengthArray[1];
        System.arraycopy(rtpPacketRawData, 0, newRtpData, 4, rtpDataLength);

        ByteBuf rtpBuf = Unpooled.copiedBuffer(newRtpData);
        channelContext.writeAndFlush(rtpBuf);
    }

    public void sendRtpPacketWithUdp(RtpPacket rtpPacket) {
        try {
            ByteBuf rtpBuf = Unpooled.copiedBuffer(rtpPacket.getRawData());
            if (rtpBuf == null || rtpBuf.readableBytes() <= 0
                    || destIp == null || rtpDestPort <= 0) {
                return;
            }

            if (rtpDestChannel != null) {
                ChannelFuture channelFuture = rtpDestChannel.writeAndFlush(rtpBuf);
                if (channelFuture == null && !isRtpDestActive()) {
                    log.warn("({}) Fail to send the message to rtp target. (ip={}, port={})", getKey(), destIp, rtpDestPort);
                    closeRtpTarget();
                } /*else {
                    log.debug("RtpPacket: ts={}, seq={}, ssrc={} / destIp={}, rtpDestPort={}",
                            rtpPacket.getTimestamp(), rtpPacket.getSeqNumber(), rtpPacket.getSyncSource(),
                            destIp, rtpDestPort
                    );

                    processRtcpPacket(rtpPacket);
                }*/
            }
        } catch (Exception e) {
            log.warn("({}) Streamer.send.Exception", getKey(), e);
        }
    }

    private void processRtcpPacket(RtpPacket rtpPacket) {
        if (rtcpDestChannel != null) {
            if (curRtcpSrCount < RTCP_SR_LIMIT_COUNT) {
                curRtcpSrCount++;
                spc += rtpPacket.getPayloadLength();
                return;
            }

            // RTCP SR
            RtcpSenderReport rtcpSenderReport = getRtcpSenderReport(rtpPacket);
            ByteBuf rtcpBuf = Unpooled.copiedBuffer(rtcpSenderReport.getData());
            if (rtcpBuf == null || rtcpBuf.readableBytes() <= 0 || destIp == null || rtcpDestPort <= 0) {
                log.trace("({}) Fail to send the message. RtcpBuf is not defined. (ip={}, port={}, bytes={})",
                        getKey(), destIp, rtcpDestPort, Objects.requireNonNull(rtcpBuf).readableBytes()
                );
                return;
            }

            ChannelFuture rtcpChannelFuture = rtcpDestChannel.writeAndFlush(rtcpBuf);
            if (rtcpChannelFuture == null && !isRtcpDestActive()) {
                log.warn("({}) Fail to send the message to rtcp target. (ip={}, port={})", getKey(), destIp, rtcpDestPort);
                closeRtcpTarget();
            }
        }
    }

    private RtcpSenderReport getRtcpSenderReport(RtpPacket rtpPacket) {
        long curSeconds = TimeStamp.getCurrentTime().getSeconds();
        long curFraction = TimeStamp.getCurrentTime().getFraction();
        long rtpTimestamp = rtpPacket.getTimestamp();
        long rtpSeqNum = rtpPacket.getSeqNumber();

        // REPORT BLOCK LIST
        List<RtcpReportBlock> rtcpReportBlockList = new ArrayList<>();
        RtcpReportBlock source1 = new RtcpReportBlock(
                rtpPacket.getSyncSource(), (byte) curFraction, 0,
                rtpSeqNum, 0,
                curSeconds, 0
        );
        rtcpReportBlockList.add(source1);

        RtcpSenderReport rtcpSenderReport = new RtcpSenderReport(
                curSeconds,
                curFraction, rtpTimestamp,
                curRtcpSrCount, spc,
                rtcpReportBlockList,
                null
        );

        curRtcpSrCount = 0;
        spc = 0;
        log.debug("({}) RtcpSenderReport: \n{}", getKey(), rtcpSenderReport);
        return rtcpSenderReport;
    }

    public void setCongestionLevel(int congestionLevel) {
        this.congestionLevel = congestionLevel;
    }

    public int getCongestionLevel() {
        return congestionLevel;
    }

    public DefaultFullHttpResponse getPlayResponse() {
        return playResponse;
    }

    public void setPlayResponse(DefaultFullHttpResponse playResponse) {
        playResponseLock.lock();
        try {
            this.playResponse = playResponse;
        } catch (Exception e) {
            // ignore
        } finally {
            playResponseLock.unlock();
        }
    }

    public String getTrackId() {
        return trackId;
    }

    public String getKey() {
        return (trackId != null && !trackId.isEmpty()) ?
                callId + ":" + trackId : callId;
    }

}
