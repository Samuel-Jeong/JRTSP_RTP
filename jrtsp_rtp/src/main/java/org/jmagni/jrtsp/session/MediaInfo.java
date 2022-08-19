package org.jmagni.jrtsp.session;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
public class MediaInfo {

    private int audioPayloadType = 0;
    private int videoPayloadType = 0;

}
