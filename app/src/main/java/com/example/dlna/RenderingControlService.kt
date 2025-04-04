package com.example.dlna

import org.fourthline.cling.binding.annotations.UpnpAction
import org.fourthline.cling.binding.annotations.UpnpInputArgument
import org.fourthline.cling.binding.annotations.UpnpOutputArgument
import org.fourthline.cling.binding.annotations.UpnpService
import org.fourthline.cling.binding.annotations.UpnpServiceId
import org.fourthline.cling.binding.annotations.UpnpServiceType
import org.fourthline.cling.binding.annotations.UpnpStateVariable
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes
import org.fourthline.cling.model.types.UnsignedIntegerTwoBytes

/**
 * UPnP渲染控制服务
 * 
 * 该服务实现了UPnP RenderingControl服务规范，用于控制设备的音量和静音状态。
 * 遵循UPnP/DLNA标准，提供对音频渲染属性的管理功能。
 * @author Max
 */
@UpnpService(
    serviceId = UpnpServiceId("RenderingControl"),
    serviceType = UpnpServiceType(value = "RenderingControl", version = 1)
)
class RenderingControlService {

    /** 实例ID状态变量 */
    @UpnpStateVariable(defaultValue = "0", sendEvents = false, name = "InstanceID")
    private var instanceId: UnsignedIntegerFourBytes? = null

    /** 声道状态变量 */
    @UpnpStateVariable(defaultValue = "Master", sendEvents = false, name = "Channel")
    private var channel: String? = null

    /** 音量状态变量 */
    @UpnpStateVariable(defaultValue = "0")
    private var volume: UnsignedIntegerTwoBytes? = null

    /** 静音状态变量 */
    @UpnpStateVariable(defaultValue = "0", datatype = "boolean")
    private var mute: String = "0"

    /** 设置音量 */
    @UpnpAction
    fun setVolume(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String,
        @UpnpInputArgument(name = "DesiredVolume") desiredVolume: UnsignedIntegerTwoBytes
    ) {
        this.volume = desiredVolume
    }

    /** 设置静音状态 */
    @UpnpAction
    fun setMute(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String,
        @UpnpInputArgument(name = "DesiredMute") desiredMute: String
    ) {
        this.mute = desiredMute
    }

    /** 获取当前音量 */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentVolume")])
    fun getVolume(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String
    ): UnsignedIntegerTwoBytes? {
        return volume
    }

    /** 获取当前静音状态 */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentMute")])
    fun getMute(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String
    ): String {
        return mute
    }
} 