package org.jmagni.jrtsp.rtsp.sdp.base.time;


import org.jmagni.jrtsp.rtsp.sdp.base.SdpFactory;
import org.jmagni.jrtsp.rtsp.sdp.base.field.TimeField;

/**
 * @class public class TimeDescriptionFactory
 * @brief TimeDescriptionFactory class
 */
public class TimeDescriptionFactory extends SdpFactory {

    // Mandatory
    private TimeField timeField;

    ////////////////////////////////////////////////////////////////////////////////

    public TimeDescriptionFactory(
            char timeType,
            String startTime, String endTime) {
        this.timeField = new TimeField(
                timeType,
                startTime,
                endTime
        );
    }

    ////////////////////////////////////////////////////////////////////////////////

    public TimeField getTimeField() {
        return timeField;
    }

    public void setTimeField(TimeField timeField) {
        this.timeField = timeField;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public String getData () {
        return timeField.getTimeType() + "=" +
                timeField.getStartTime() + " " +
                timeField.getEndTime() +
                CRLF;
    }

}
