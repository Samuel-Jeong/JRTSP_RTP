package org.jmagni.jrtsp.rtsp.sdp.base.media;

import org.jmagni.jrtsp.rtsp.sdp.base.SdpFactory;
import org.jmagni.jrtsp.rtsp.sdp.base.attribute.RtpAttribute;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @class public class MediaDescriptionFactory
 * @brief MediaDescriptionFactory class
 */
public class MediaDescriptionFactory extends SdpFactory {

    private final Map<String, MediaFactory> mediaFactoryMap = new HashMap<>();

    ////////////////////////////////////////////////////////////////////////////////

    public MediaDescriptionFactory() {
        // Nothing
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void addMediaFactory(MediaFactory mediaFactory) {
        if (mediaFactoryMap.get(mediaFactory.getMediaField().getMediaType()) != null) {
            return;
        }

        mediaFactoryMap.putIfAbsent(
                mediaFactory.getMediaField().getMediaType(),
                mediaFactory
        );
    }

    public MediaFactory getMediaFactory(String mediaType) {
        return mediaFactoryMap.get(mediaType);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public String getData (boolean isRaw) {
        StringBuilder data = new StringBuilder();

        for (Map.Entry<String, MediaFactory> entry : mediaFactoryMap.entrySet()) {
            if (entry == null) {
                continue;
            }

            MediaFactory mediaFactory = entry.getValue();
            if (mediaFactory == null) {
                continue;
            }

            data.append(
                    mediaFactory.getData(
                            isRaw
                    )
            );
        }

        return data.toString();
    }

    ////////////////////////////////////////////////////////////////////////////////

    public List<RtpAttribute> getCodecList (String mediaType) {
        if (mediaFactoryMap.get(mediaType) != null) {
            return mediaFactoryMap.get(mediaType).getCodecList();
        }
        return Collections.emptyList();
    }

}
