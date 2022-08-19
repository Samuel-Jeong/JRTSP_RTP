package org.jmagni.jrtsp.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ntp.TimeStamp;
import org.jmagni.jrtsp.config.base.DefaultConfig;
import org.jmagni.jrtsp.rtsp.sdp.SdpParser;
import org.jmagni.jrtsp.rtsp.sdp.base.Sdp;

@Getter
@Slf4j
public class UserConfig extends DefaultConfig {

    private static final String SECTION_COMMON = "COMMON";
    private static final String SECTION_RTSP_NETWORK = "RTSP_NETWORK";
    private static final String SECTION_RTSP_SDP = "RTSP_SDP";

    public static final String FIELD_ID = "ID";
    public static final String FIELD_SEND_BUF_SIZE = "SEND_BUF_SIZE";
    public static final String FIELD_RECV_BUF_SIZE = "RECV_BUF_SIZE";
    public static final String FIELD_STREAM_THREAD_POOL_SIZE = "STREAM_THREAD_POOL_SIZE";
    public static final String FIELD_LOCAL_LISTEN_IP = "LOCAL_LISTEN_IP";
    public static final String FIELD_LOCAL_RTSP_LISTEN_PORT = "LOCAL_RTSP_LISTEN_PORT";
    public static final String FIELD_LOCAL_RTCP_PORT_MIN = "LOCAL_RTCP_PORT_MIN";
    public static final String FIELD_LOCAL_RTCP_PORT_MAX = "LOCAL_RTCP_PORT_MAX";

    private String id = null;
    private int sendBufSize = 0;
    private int recvBufSize = 0;
    private int streamThreadPoolSize = 1;
    private String localListenIp = null;
    private int localRtspListenPort = 0;
    private int localRtcpPortMin = 0;
    private int localRtcpPortMax = 0;

    private final SdpParser sdpParser = new SdpParser();
    private String version;
    private String origin;
    private String session;
    private String time;
    private String connection;
    private String audio;
    private String audioRtpMap;
    private String video;
    private String videoRtpMap;
    String[] audioAttributeList;
    String[] videoAttributeList;

    public UserConfig(String configFileName) {
        super(configFileName);

        if (load()) {
            loadConfig();
        }

        setConfigChangedListener((boolean changed) -> {
            if (changed) {
                logger.warn("() () () user configuration is changed by user.");
                loadConfig();
            }
        });
    }

    private void loadConfig ( ) {
        loadCommonConfig();
        loadNetworkConfig();
        loadRtspSdpConfig();
    }

    private void loadCommonConfig() {
        id = getStrValue(SECTION_COMMON, FIELD_ID, null);
        if (id == null) {
            logger.error("[{}] {} IS NOT DEFINED.", SECTION_COMMON, FIELD_ID);
            System.exit(1);
        }

        logger.debug("Load [{}] config...(OK)", SECTION_COMMON);
    }

    private void loadNetworkConfig() {
        this.sendBufSize = getIntValue(SECTION_RTSP_NETWORK, FIELD_SEND_BUF_SIZE, 0);
        if (this.sendBufSize <= 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_RTSP_NETWORK, FIELD_SEND_BUF_SIZE, sendBufSize);
            System.exit(1);
        }

        this.recvBufSize = getIntValue(SECTION_RTSP_NETWORK, FIELD_RECV_BUF_SIZE, 0);
        if (this.recvBufSize <= 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_RTSP_NETWORK, FIELD_RECV_BUF_SIZE, recvBufSize);
            System.exit(1);
        }

        this.streamThreadPoolSize = getIntValue(SECTION_RTSP_NETWORK, FIELD_STREAM_THREAD_POOL_SIZE, 0);
        if (this.streamThreadPoolSize <= 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_RTSP_NETWORK, FIELD_STREAM_THREAD_POOL_SIZE, streamThreadPoolSize);
            System.exit(1);
        }

        this.localListenIp = getStrValue(SECTION_RTSP_NETWORK, FIELD_LOCAL_LISTEN_IP, null);
        if (this.localListenIp == null) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_RTSP_NETWORK, FIELD_LOCAL_LISTEN_IP, localListenIp);
            System.exit(1);
        }

        this.localRtspListenPort = getIntValue(SECTION_RTSP_NETWORK, FIELD_LOCAL_RTSP_LISTEN_PORT, 0);
        if (this.localRtspListenPort <= 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_RTSP_NETWORK, FIELD_LOCAL_RTSP_LISTEN_PORT, localRtspListenPort);
            System.exit(1);
        }

        this.localRtcpPortMin = getIntValue(SECTION_RTSP_NETWORK, FIELD_LOCAL_RTCP_PORT_MIN, 0);
        if (this.localRtcpPortMin <= 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_RTSP_NETWORK, FIELD_LOCAL_RTCP_PORT_MIN, localRtcpPortMin);
            System.exit(1);
        }

        this.localRtcpPortMax = getIntValue(SECTION_RTSP_NETWORK, FIELD_LOCAL_RTCP_PORT_MAX, 0);
        if (this.localRtcpPortMax <= 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_RTSP_NETWORK, FIELD_LOCAL_RTCP_PORT_MAX, localRtcpPortMax);
            System.exit(1);
        }

        if (localRtcpPortMin > localRtcpPortMax) {
            logger.error("Fail to load [{}]. RtpPortRange is wrong. ({}-{})", SECTION_RTSP_NETWORK, localRtcpPortMin, localRtcpPortMax);
            System.exit(1);
        }

        logger.debug("Load [{}] config...(OK)", SECTION_RTSP_NETWORK);
    }

    private void loadRtspSdpConfig() {
        version = getStrValue(SECTION_RTSP_SDP, "VERSION", null);
        if (version == null) {
            logger.error("[SECTION_SDP] VERSION IS NOT DEFINED IN THE LOCAL SDP.");
            System.exit(1);
        }
        version = "v=" + version + "\r\n";

        origin = getStrValue(SECTION_RTSP_SDP, "ORIGIN", null);
        if (origin == null) {
            logger.error("[SECTION_SDP] ORIGIN IS NOT DEFINED IN THE LOCAL SDP.");
            System.exit(1);
        }

        session = getStrValue(SECTION_RTSP_SDP, "SESSION", null);
        if (session == null) {
            logger.error("[SECTION_SDP] SESSION IS NOT DEFINED IN THE LOCAL SDP.");
            System.exit(1);
        }
        session = "s=" + session + "\r\n";

        time = getStrValue(SECTION_RTSP_SDP, "TIME", null);
        if (time == null) {
            logger.error("[SECTION_SDP] TIME IS NOT DEFINED IN THE LOCAL SDP.");
            System.exit(1);
        }
        time = "t=" + time + "\r\n";

        connection = getStrValue(SECTION_RTSP_SDP, "CONNECTION", null);
        if (connection == null) {
            logger.error("[SECTION_SDP] CONNECTION IS NOT DEFINED IN THE LOCAL SDP.");
            System.exit(1);
        }

        audio = getStrValue(SECTION_RTSP_SDP, "AUDIO", null);
        audioRtpMap = getStrValue(SECTION_RTSP_SDP, "AUDIO_RTPMAP", null);
        video = getStrValue(SECTION_RTSP_SDP, "VIDEO", null);
        videoRtpMap = getStrValue(SECTION_RTSP_SDP, "VIDEO_RTPMAP", null);

        int audioAttrCount = Integer.parseInt(getStrValue(SECTION_RTSP_SDP, "AUDIO_ATTR_COUNT", null));
        if (audioAttrCount < 0) {
            logger.error("[SECTION_SDP] AUDIO_ATTR_COUNT IS NOT DEFINED IN THE LOCAL SDP.");
            System.exit(1);
        }
        audioAttributeList = new String[audioAttrCount];
        for (int i = 0; i < audioAttrCount; i++) {
            String attribute = getStrValue(SECTION_RTSP_SDP, String.format("AUDIO_ATTR_%d", i), null);
            if (attribute != null) {
                audioAttributeList[i] = attribute;
            }
        }

        int videoAttrCount = Integer.parseInt(getStrValue(SECTION_RTSP_SDP, "VIDEO_ATTR_COUNT", null));
        if (videoAttrCount < 0) {
            logger.error("[SECTION_SDP] VIDEO_ATTR_COUNT IS NOT DEFINED IN THE LOCAL SDP.");
            System.exit(1);
        }
        videoAttributeList = new String[videoAttrCount];
        for (int i = 0; i < videoAttrCount; i++) {
            String attribute = getStrValue(SECTION_RTSP_SDP, String.format("VIDEO_ATTR_%d", i), null);
            if (attribute != null) {
                videoAttributeList[i] = attribute;
            }
        }

        logger.debug("Load [{}] config...(OK)", SECTION_RTSP_SDP);
    }

    public Sdp loadLocalSdpConfig(String id, int localPort, int audioPayloadType, int videoPayloadType) {
        try {
            StringBuilder sdpStr = new StringBuilder();

            // 1) Session
            // 1-1) Version
            sdpStr.append(version);

            // 1-2) Origin
            /*
                - Using NTP Timestamp
                [RFC 4566]
                  <sess-id> is a numeric string such that the tuple of <username>,
                  <sess-id>, <nettype>, <addrtype>, and <unicast-address> forms a
                  globally unique identifier for the session.  The method of
                  <sess-id> allocation is up to the creating tool, but it has been
                  suggested that a Network Time Protocol (NTP) format timestamp be
                  used to ensure uniqueness.
             */
            String originSessionId = String.valueOf(TimeStamp.getCurrentTime().getTime());
            String curOrigin = String.format(this.origin, originSessionId, localListenIp);
            curOrigin = "o=" + curOrigin + "\r\n";
            sdpStr.append(curOrigin);

            // 1-3) Session
            sdpStr.append(session);

            // 3) Media
            // 3-1) Connection
            String connection = String.format(this.connection, localListenIp);
            connection = "c=" + connection + "\r\n";
            sdpStr.append(connection);

            // 2) Time
            // 2-1) Time
            sdpStr.append(time);

            // 3) Media
            // 3-2) Media
            if (audio != null && !audio.isEmpty()) {
                sdpStr.append("m=");
                if (audioPayloadType != 0) {
                    sdpStr.append(String.format(this.audio, localPort, audioPayloadType));
                } else {
                    sdpStr.append(String.format(this.audio, localPort, 0)); // Default : PCMU
                }
                sdpStr.append("\r\n");
            }

            if (audioRtpMap != null && !audioRtpMap.isEmpty()) {
                sdpStr.append("a=");
                sdpStr.append(String.format(this.audioRtpMap, audioPayloadType));
                sdpStr.append("\r\n");
            }

            for (String attribute : audioAttributeList) {
                sdpStr.append("a=");
                sdpStr.append(attribute);
                sdpStr.append("\r\n");
            }

            if (video != null && !video.isEmpty()) {
                sdpStr.append("m=");
                if (videoPayloadType != 0) {
                    sdpStr.append(String.format(this.video, localPort, videoPayloadType));
                    logger.debug("this.video: {} / videoPayloadType: {}", this.video, videoPayloadType);
                } else {
                    sdpStr.append(String.format(this.video, localPort, 96)); // Default dynamic type : 96
                }
                sdpStr.append("\r\n");
            }

            if (videoRtpMap != null && !videoRtpMap.isEmpty()) {
                sdpStr.append("a=");
                sdpStr.append(String.format(this.videoRtpMap, videoPayloadType));
                sdpStr.append("\r\n");
            }

            for (String attribute : videoAttributeList) {
                sdpStr.append("a=");
                sdpStr.append(attribute);
                sdpStr.append("\r\n");
            }

            Sdp localSdp = null;
            try {
                localSdp = sdpParser.parseSdp(id, null, null, sdpStr.toString());
                logger.debug("({}) Local SDP=\n{}", id, localSdp.getData(false));
            } catch (Exception e) {
                logger.error("({}) Fail to parse the local sdp. ({})", id, sdpStr, e);
                System.exit(1);
            }
            return localSdp;
        } catch (Exception e) {
            logger.warn("Fail to load the local sdp.", e);
            return null;
        }
    }

}
