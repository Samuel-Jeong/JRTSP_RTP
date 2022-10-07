package org.jmagni.jrtsp.rtsp.stream.rtp;

import lombok.Data;
import org.jmagni.jrtsp.rtsp.base.RtpPacket;

@Data
public class RtpDto {

    private final RtpPacket rtpPacket;
    private final String mediaType;

}
