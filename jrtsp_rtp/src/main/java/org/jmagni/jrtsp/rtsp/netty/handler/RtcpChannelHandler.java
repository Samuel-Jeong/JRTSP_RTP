package org.jmagni.jrtsp.rtsp.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.jmagni.jrtsp.rtsp.Streamer;
import org.jmagni.jrtsp.rtsp.base.ByteUtil;
import org.jmagni.jrtsp.rtsp.netty.NettyChannelManager;
import org.jmagni.jrtsp.rtsp.rtcp.base.RtcpType;
import org.jmagni.jrtsp.rtsp.rtcp.packet.RtcpPacket;
import org.jmagni.jrtsp.rtsp.rtcp.type.regular.RtcpReceiverReport;
import org.jmagni.jrtsp.rtsp.rtcp.type.regular.base.RtcpHeader;
import org.jmagni.jrtsp.rtsp.rtcp.type.regular.base.report.RtcpReportBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @class public class RtcpChannelHandler extends SimpleChannelInboundHandler<DatagramPacket>
 */
public class RtcpChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(RtcpChannelHandler.class);

    private final String name;
    private final String listenIp;
    private final int listenPort;

    ////////////////////////////////////////////////////////////////////////////////

    public RtcpChannelHandler(String listenIp, int listenPort) {
        this.name = "RTCP_" + listenIp + ":" + listenPort;

        this.listenIp = listenIp;
        this.listenPort = listenPort;

        logger.debug("({}) RtcpChannelHandler is created. (listenIp={}, listenPort={})", name, listenIp, listenPort);
    }

    ////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void channelRead0 (ChannelHandlerContext ctx, DatagramPacket msg) {
        try {
            ByteBuf buf = msg.content();
            if (buf == null) {
                return;
            }

            int readBytes = buf.readableBytes();
            if (readBytes <= 0) {
                return;
            }

            byte[] data = new byte[readBytes];
            buf.getBytes(0, data);

            logger.debug("({}) data: [{}], readBytes: [{}]", name, ByteUtil.byteArrayToHex(data), readBytes);

            if (data.length >= RtcpHeader.LENGTH) {
                RtcpPacket rtcpPacket = new RtcpPacket(data);
                logger.debug("({}) {}", name, rtcpPacket);

                int packetType = rtcpPacket.getRtcpHeader().getPacketType();
                switch (packetType) {
                    case RtcpType.RECEIVER_REPORT:
                        for (Streamer streamer : NettyChannelManager.getInstance().getAllStreamers()) {
                            if (streamer == null) { continue; }

                            long audioSsrc = streamer.getAudioSsrc();
                            if (audioSsrc > 0) {
                                handleReceiverReport(rtcpPacket, streamer, audioSsrc);
                            }

                            long videoSsrc = streamer.getVideoSsrc();
                            if (videoSsrc > 0) {
                                handleReceiverReport(rtcpPacket, streamer, videoSsrc);
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            logger.warn("| ({}) Fail to handle the rtcp Packet.", name, e);
        }
    }

    private static void handleReceiverReport(RtcpPacket rtcpPacket, Streamer streamer, long ssrc) {
        RtcpReceiverReport rtcpReceiverReport = (RtcpReceiverReport) rtcpPacket.getRtcpFormat();
        RtcpReportBlock rtcpReportBlock = rtcpReceiverReport.getReportBlockBySsrc(ssrc);
        if (rtcpReportBlock != null) {
            float fractionLost = ((float) rtcpReportBlock.getFraction() / 100);
            if (fractionLost >= 0 && fractionLost <= 0.01) {
                streamer.setCongestionLevel(0);
            } else if (fractionLost > 0.01 && fractionLost <= 0.25) {
                streamer.setCongestionLevel(1);
            } else if (fractionLost > 0.25 && fractionLost <= 0.5) {
                streamer.setCongestionLevel(2);
            } else if (fractionLost > 0.5 && fractionLost <= 0.75) {
                streamer.setCongestionLevel(3);
            } else {
                streamer.setCongestionLevel(4);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public String getName() {
        return name;
    }

    public String getListenIp() {
        return listenIp;
    }

    public int getListenPort() {
        return listenPort;
    }
}
