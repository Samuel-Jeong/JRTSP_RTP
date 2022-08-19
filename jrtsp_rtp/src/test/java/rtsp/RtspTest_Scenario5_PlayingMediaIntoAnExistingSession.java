package rtsp;


/**
 * @apiNote https://www.rfc-editor.org/rfc/rfc2326#page-34
 *
 *       method            direction        object     requirement
 *       DESCRIBE          C->S             P,S        recommended
 *       ANNOUNCE          C->S, S->C       P,S        optional
 *       GET_PARAMETER     C->S, S->C       P,S        optional
 *       OPTIONS           C->S, S->C       P,S        required
 *                                                     (S->C: optional)
 *       PAUSE             C->S             P,S        recommended
 *       PLAY              C->S             P,S        required
 *       RECORD            C->S             P,S        optional
 *       REDIRECT          S->C             P,S        optional
 *       SETUP             C->S             S          required
 *       SET_PARAMETER     C->S, S->C       P,S        optional
 *       TEARDOWN          C->S             P,S        required
 */

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.rtsp.RtspHeaderNames;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspVersions;
import lombok.extern.slf4j.Slf4j;
import org.jmagni.jrtsp.rtsp.Streamer;
import org.jmagni.jrtsp.rtsp.netty.NettyChannelManager;
import org.jmagni.jrtsp.rtsp.netty.handler.RtspChannelHandler;
import org.jmagni.jrtsp.session.SessionManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * @implNote Test Scenario 1 : 14.5 Playing media into an existing session
 * @implSpec C : Client
 * @implSpec S : Server
 *
 *      C->S: DESCRIBE rtsp://server.example.com/call_id_test_1 RTSP/1.0
 *            CSeq: 1
 *            Accept: application/sdp
 *
 *      S->C: RTSP/1.0 200 OK
 *            CSeq: 1
 *            Content-Type: application/sdp
 *            Content-Length: 109
 *
 *            v=0
 *            o=W_AMF_0 1660008599150 0 IN IP4 127.0.0.1
 *            s=streaming
 *            c=IN IP4 127.0.0.1
 *            t=0 0
 *            m=audio 0 RTP/AVP 0
 *            a=rtpmap:0 PCMU/8000
 *            a=control:trackID=1
 *            a=sendonly
 *            m=video 0 RTP/AVP 108
 *            a=control:trackID=2
 *            a=sendonly
 *
 *      C->S SETUP rtsp://server.example.com/call_id_test_1/trackID=1 RTSP/1.0
 *            CSeq: 2
 *            Transport: RTP/AVP;multicast;destination=127.0.0.1;
 *                       port=3456-3457;ttl=16
 *
 *      S->C: RTSP/1.0 200 OK
 *            CSeq: 2
 *            Transport: RTP/AVP;multicast;destination=127.0.0.1;
 *                       port=3456-3457;ttl=16
 *            Session: 0456804596
 *
 *      C->S SETUP rtsp://server.example.com/call_id_test_1/trackID=2 RTSP/1.0
 *            CSeq: 3
 *            Transport: RTP/AVP;multicast;destination=127.0.0.1;
 *                       port=3460-3461;ttl=16
 *
 *      S->C: RTSP/1.0 200 OK
 *            CSeq: 3
 *            Transport: RTP/AVP;multicast;destination=127.0.0.1;
 *                       port=3460-3461;ttl=16
 *            Session: 0456804596
 *
 *      C->S: PLAY rtsp://server.example.com/call_id_test_1 RTSP/1.0
 *            CSeq: 4
 *            Session: 0456804596
 *
 *      S->C: RTSP/1.0 200 OK
 *            CSeq: 4
 *            Session: 0456804596
 *
 *      C->S: TEARDOWN rtsp://server.example.com/call_id_test_1 RTSP/1.0
 *            CSeq: 5
 *            Session: 0456804596
 *
 *      S->C: RTSP/1.0 200 OK
 *            CSeq: 5
 *            Session: 0456804596
 */
@Slf4j
public class RtspTest_Scenario5_PlayingMediaIntoAnExistingSession {

    private EmbeddedChannel ch = null;

    private final String conferenceId = "conference_id_test_1";
    private final String callId = "call_id_test_1";
    private final String uri = "rtsp://server.example.com/call_id_test_1";

    private final String audioUri = "rtsp://server.example.com/call_id_test_1/trackID=1";
    private final int clientAudioRtpPort = 3456;
    private final int clientAudioRtcpPort = 3457;

    private final String videoUri = "rtsp://server.example.com/call_id_test_1/trackID=2";
    private final int clientVideoRtpPort = 3460;
    private final int clientVideoRtcpPort = 3461;

    private String sessionId = null;

    public void options() throws Exception {
        // 1) Given
        /**
         * C->S:  OPTIONS * RTSP/1.0
         *             CSeq: 1
         *             Require: implicit-play
         *             Proxy-Require: gzipped-messages
         */
        FullHttpRequest options = new DefaultFullHttpRequest(
                RtspVersions.RTSP_1_0,
                RtspMethods.OPTIONS,
                ""
        );

        options.headers().add(RtspHeaderNames.CSEQ, "1");

        // 2) When
        sendHttpRequest(options);

        // 3) Then

    }

    public void describe() throws Exception {
        // 1) Given
        /**
         * C->S: DESCRIBE rtsp://server.example.com/ccall_id_test_1 RTSP/1.0
         *            CSeq: 1
         */
        FullHttpRequest describe = new DefaultFullHttpRequest(
                RtspVersions.RTSP_1_0,
                RtspMethods.DESCRIBE,
                uri
        );

        describe.headers().add(RtspHeaderNames.CSEQ, "1");
        describe.headers().add(RtspHeaderNames.ACCEPT, "application/sdp");

        // 2) When
        sendHttpRequest(describe);

        // 3) Then
    }
    
    public void audioSetup() throws Exception {
        // 1) Given
        /**
         *  C->A: SETUP rtsp://server.example.com/call_id_test_1/trackID=1 RTSP/1.0
         *            CSeq: 2
         *            Transport: RTP/AVP;multicast;destination=127.0.0.1;port=3456-3457;ttl=16
         */
        HttpRequest setup = new DefaultHttpRequest(
                RtspVersions.RTSP_1_0,
                RtspMethods.SETUP,
                audioUri
        );

        setup.headers().add(
                RtspHeaderNames.TRANSPORT,
                "RTP/AVP;multicast;destination=127.0.0.1;port=" + clientAudioRtpPort + "-" + clientAudioRtcpPort
        );
        setup.headers().add(RtspHeaderNames.CSEQ, "2");
        
        // 2) When
        sendHttpRequest(setup);

        // 3) Then
        List<Streamer> streamerList = NettyChannelManager.getInstance().getStreamerListByCallId(callId);
        assertNotNull(streamerList);
        assertNotEquals(streamerList.size(), 1);

        Streamer streamer = NettyChannelManager.getInstance().getStreamerByUri(uri);
        assertNotNull(streamer);

        sessionId = streamer.getSessionId();

        assertEquals(uri, streamer.getUri());
        assertEquals(clientAudioRtpPort, streamer.getRtpDestPort());
        assertEquals(clientAudioRtcpPort, streamer.getRtcpDestPort());
    }

    public void videoSetup() throws Exception {
        // 1) Given
        /**
         * SETUP rtsp://server.example.com/call_id_test_1/trackID=2 RTSP/1.0
         *            CSeq: 2
         *            Transport: RTP/AVP;multicast;destination=127.0.0.1;port=3460-3461;ttl=16
         */
        HttpRequest setup = new DefaultHttpRequest(
                RtspVersions.RTSP_1_0,
                RtspMethods.SETUP,
                videoUri
        );

        setup.headers().add(
                RtspHeaderNames.TRANSPORT,
                "RTP/AVP;multicast;destination=127.0.0.1;port=" + clientVideoRtpPort + "-" + clientVideoRtcpPort + ";ttl=16"
        );
        setup.headers().add(RtspHeaderNames.CSEQ, "3");

        // 2) When
        sendHttpRequest(setup);

        // 3) Then
        List<Streamer> streamerList = NettyChannelManager.getInstance().getStreamerListByCallId(callId);
        assertNotNull(streamerList);
        assertNotEquals(streamerList.size(), 2);

        Streamer streamer = NettyChannelManager.getInstance().getStreamerByUri(uri);
        assertNotNull(streamer);

        sessionId = streamer.getSessionId();

        assertEquals(uri, streamer.getUri());
        assertEquals(clientVideoRtpPort, streamer.getRtpDestPort());
        assertEquals(clientVideoRtcpPort, streamer.getRtcpDestPort());
    }

    public void play() throws Exception {
        // 1) Given
        List<Streamer> streamerList = NettyChannelManager.getInstance().getStreamerListByCallId(callId);
        assertNotNull(streamerList);
        assertNotEquals(streamerList.size(), 2);

        /**
         * C->A: PLAY rtsp://server.example.com/call_id_test_1 RTSP/1.0
         *            CSeq: 3
         *            Session: 12345678
         */
        HttpRequest play = new DefaultHttpRequest(
                RtspVersions.RTSP_1_0,
                RtspMethods.PLAY,
                audioUri
        );

        play.headers().add(RtspHeaderNames.CSEQ, 4);
        play.headers().add(RtspHeaderNames.SESSION, NettyChannelManager.getInstance().getStreamerByUri(uri).getSessionId());

        // 2) When
        sendHttpRequest(play);

        // 3) Then
        Streamer streamer = NettyChannelManager.getInstance().getStreamerByUri(uri);
        assertNotNull(streamer);

        assertEquals(uri, streamer.getUri());
        assertEquals(0, (int) streamer.getStartTime());
        assertEquals(0, (int) streamer.getEndTime());

        //String curState = rtspUnit.getStateManager().getStateUnit(rtspUnit.getRtspStateUnitId()).getCurState();
        //assertEquals(RtspState.PLAY, curState);
    }

    public void teardown() throws Exception {
        // 1) Given
        List<Streamer> streamerList = NettyChannelManager.getInstance().getStreamerListByCallId(callId);
        assertNotNull(streamerList);
        assertNotEquals(streamerList.size(), 2);

        /**
         * C->A: TEARDOWN rtsp://server.example.com/call_id_test_1 RTSP/1.0
         *            CSeq: 4
         *            Session: 12345678
         */

        HttpRequest teardown = new DefaultHttpRequest(
                RtspVersions.RTSP_1_0,
                RtspMethods.TEARDOWN,
                uri
        );

        teardown.headers().add(RtspHeaderNames.CSEQ, 5);

        String sessionId = NettyChannelManager.getInstance().getStreamerByUri(uri).getSessionId();
        teardown.headers().add(RtspHeaderNames.SESSION, sessionId);

        // 2) When
        sendHttpRequest(teardown);

        // 3) Then
        int streamerSize = NettyChannelManager.getInstance().getAllStreamers().size();
        assertEquals(0, streamerSize);
    }

    private void sendHttpRequest(HttpRequest httpRequest) {
        ch.writeInbound(httpRequest);
    }

    @Test
    public void testAll() throws Exception {
        RtspTotalTest.init();

        ch = new EmbeddedChannel(new RtspChannelHandler("127.0.0.1", 5000));

        SessionManager.getInstance().createCall(
                conferenceId,
                callId,
                true
        );

        options();

        describe();

        audioSetup();
        videoSetup();

        play();

        teardown();

        RtspTotalTest.close();
    }

}
