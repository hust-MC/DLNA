package com.example.dlna;

import org.fourthline.cling.binding.annotations.UpnpAction;
import org.fourthline.cling.binding.annotations.UpnpInputArgument;
import org.fourthline.cling.binding.annotations.UpnpOutputArgument;
import org.fourthline.cling.binding.annotations.UpnpService;
import org.fourthline.cling.binding.annotations.UpnpServiceId;
import org.fourthline.cling.binding.annotations.UpnpServiceType;
import org.fourthline.cling.binding.annotations.UpnpStateVariable;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;

@UpnpService(
    serviceId = @UpnpServiceId("AVTransport"),
    serviceType = @UpnpServiceType(value = "AVTransport", version = 1)
)
public class MediaRendererService {

    @UpnpStateVariable(defaultValue = "0", sendEvents = false, name = "InstanceID")
    private UnsignedIntegerFourBytes instanceId;

    @UpnpStateVariable(defaultValue = "STOPPED")
    private String transportState;

    @UpnpStateVariable(defaultValue = "1")
    private String currentPlayMode;

    @UpnpStateVariable(defaultValue = "0")
    private String currentTrack;

    @UpnpStateVariable(defaultValue = "00:00:00")
    private String currentTrackDuration;

    @UpnpStateVariable(defaultValue = "00:00:00")
    private String currentTrackMetaData;

    @UpnpStateVariable(defaultValue = "00:00:00")
    private String currentMediaDuration;

    @UpnpStateVariable(defaultValue = "")
    private String currentURI;

    @UpnpStateVariable(defaultValue = "")
    private String currentURIMetaData;

    @UpnpStateVariable(defaultValue = "0")
    private String nextTrackURI;

    @UpnpStateVariable(defaultValue = "")
    private String nextTrackMetaData;

    @UpnpStateVariable(defaultValue = "0")
    private String numberOfTracks;

    @UpnpStateVariable(defaultValue = "1")
    private String speed;

    @UpnpStateVariable(defaultValue = "REL_TIME", name = "Unit")
    private String seekMode;

    @UpnpStateVariable(defaultValue = "00:00:00", name = "Target")
    private String seekTarget;

    @UpnpAction
    public void setAVTransportURI(
            @UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "CurrentURI") String uri,
            @UpnpInputArgument(name = "CurrentURIMetaData") String metadata) {
        this.currentURI = uri;
        this.currentURIMetaData = metadata;
    }

    @UpnpAction
    public void play(
            @UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "Speed") String speed) {
        this.speed = speed;
        this.transportState = "PLAYING";
    }

    @UpnpAction
    public void pause(
            @UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId) {
        this.transportState = "PAUSED_PLAYBACK";
    }

    @UpnpAction
    public void stop(
            @UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId) {
        this.transportState = "STOPPED";
    }

    @UpnpAction
    public void seek(
            @UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "Unit") String unit,
            @UpnpInputArgument(name = "Target") String target) {
        this.seekMode = unit;
        this.seekTarget = target;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentTransportState"))
    public String getTransportState() {
        return transportState;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentTrack"))
    public String getCurrentTrack() {
        return currentTrack;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentTrackDuration"))
    public String getCurrentTrackDuration() {
        return currentTrackDuration;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentMediaDuration"))
    public String getCurrentMediaDuration() {
        return currentMediaDuration;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentURI"))
    public String getCurrentURI() {
        return currentURI;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentURIMetaData"))
    public String getCurrentURIMetaData() {
        return currentURIMetaData;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "Speed"))
    public String getSpeed() {
        return speed;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "Unit"))
    public String getSeekMode() {
        return seekMode;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "Target"))
    public String getSeekTarget() {
        return seekTarget;
    }
} 