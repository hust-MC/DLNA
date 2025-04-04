package com.example.dlna;

import org.fourthline.cling.binding.annotations.UpnpAction;
import org.fourthline.cling.binding.annotations.UpnpInputArgument;
import org.fourthline.cling.binding.annotations.UpnpOutputArgument;
import org.fourthline.cling.binding.annotations.UpnpService;
import org.fourthline.cling.binding.annotations.UpnpServiceId;
import org.fourthline.cling.binding.annotations.UpnpServiceType;
import org.fourthline.cling.binding.annotations.UpnpStateVariable;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.model.types.UnsignedIntegerTwoBytes;

@UpnpService(
    serviceId = @UpnpServiceId("RenderingControl"),
    serviceType = @UpnpServiceType(value = "RenderingControl", version = 1)
)
public class RenderingControlService {

    @UpnpStateVariable(defaultValue = "0", sendEvents = false, name = "InstanceID")
    private UnsignedIntegerFourBytes instanceId;

    @UpnpStateVariable(defaultValue = "Master", sendEvents = false, name = "Channel")
    private String channel;

    @UpnpStateVariable(defaultValue = "0")
    private UnsignedIntegerTwoBytes volume;

    @UpnpStateVariable(defaultValue = "0")
    private boolean mute;

    @UpnpAction
    public void setVolume(
            @UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "Channel") String channel,
            @UpnpInputArgument(name = "DesiredVolume") UnsignedIntegerTwoBytes desiredVolume) {
        this.volume = desiredVolume;
    }

    @UpnpAction
    public void setMute(
            @UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "Channel") String channel,
            @UpnpInputArgument(name = "DesiredMute") boolean desiredMute) {
        this.mute = desiredMute;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentVolume"))
    public UnsignedIntegerTwoBytes getVolume(
            @UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "Channel") String channel) {
        return volume;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentMute"))
    public boolean getMute(
            @UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "Channel") String channel) {
        return mute;
    }
}