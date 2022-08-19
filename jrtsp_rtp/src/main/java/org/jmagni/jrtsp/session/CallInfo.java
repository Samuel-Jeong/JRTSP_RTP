package org.jmagni.jrtsp.session;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jmagni.jrtsp.rtsp.Streamer;
import org.jmagni.jrtsp.rtsp.base.MediaType;
import org.jmagni.jrtsp.rtsp.base.RtpInfo;
import org.jmagni.jrtsp.rtsp.netty.NettyChannelManager;

import java.util.List;

@Getter
@Setter
@Slf4j
public class CallInfo {

    private final String callId;
    private final String conferenceId;
    private final boolean isHost;

    private MediaInfo mediaInfo = null;

    public CallInfo(String conferenceId, String callId, boolean isHost) {
        this.conferenceId = conferenceId;
        this.callId = callId;
        this.isHost = isHost;
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
