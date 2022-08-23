package org.jmagni.jrtsp.rtsp.stream.rtp;


import lombok.extern.slf4j.Slf4j;
import org.jmagni.jrtsp.rtsp.Streamer;
import org.jmagni.jrtsp.rtsp.base.ConcurrentCyclicFIFO;
import org.jmagni.jrtsp.rtsp.base.MediaType;
import org.jmagni.jrtsp.rtsp.base.RtpInfo;
import org.jmagni.jrtsp.rtsp.netty.NettyChannelManager;

import java.util.List;

@Slf4j
public class RtpInfoSender extends Thread {

    private final String callId;
    private final ConcurrentCyclicFIFO<RtpInfo> rtpInfoBuf;

    public RtpInfoSender(String callId, ConcurrentCyclicFIFO<RtpInfo> rtpInfoBuf) {
        this.callId = callId;
        this.rtpInfoBuf = rtpInfoBuf;

        log.debug("RtpInfoSender is created. (callId={})", callId);
    }

    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            RtpInfo rtpInfo = rtpInfoBuf.poll();
            if (rtpInfo == null) { continue; }

            relayToRtspClient(rtpInfo);
        }

        log.debug("RtpInfoSender is finished. (callId={})", callId);
    }

    private void relayToRtspClient(RtpInfo rtpInfo) {
        List<Streamer> streamerList = NettyChannelManager.getInstance().getStreamerListByCallId(callId);
        if (streamerList == null || streamerList.isEmpty()) { return; }

        for (Streamer streamer : streamerList) {
            if (rtpInfo.getMediaType().equals(MediaType.AUDIO.getName())) {
                streamer.setAudioSsrc(rtpInfo.getRtpPacket().getSyncSource());
                streamer.setAudioCurSeqNum(rtpInfo.getRtpPacket().getSeqNumber());
                streamer.setAudioCurTimeStamp(rtpInfo.getRtpPacket().getTimestamp());
            } else if (rtpInfo.getMediaType().equals(MediaType.VIDEO.getName())) {
                streamer.setVideoSsrc(rtpInfo.getRtpPacket().getSyncSource());
                streamer.setVideoCurSeqNum(rtpInfo.getRtpPacket().getSeqNumber());
                streamer.setVideoCurTimeStamp(rtpInfo.getRtpPacket().getTimestamp());
            }

            // CALLBACK
            if (streamer.getPlayResponse() != null) {
                streamer.sendPlayResponse();
            }

            if (streamer.isStarted()) {
                streamer.sendRtpPacket(rtpInfo.getRtpPacket(), rtpInfo.getMediaType());
            }
        }
    }

}
