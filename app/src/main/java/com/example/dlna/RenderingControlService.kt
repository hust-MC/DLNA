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
 */
@UpnpService(
    serviceId = UpnpServiceId("RenderingControl"),
    serviceType = UpnpServiceType(value = "RenderingControl", version = 1)
)
class RenderingControlService {

    /**
     * 实例ID状态变量
     * 
     * 在UPnP中用于标识特定的服务实例。默认值为0，不发送事件通知。
     */
    @UpnpStateVariable(defaultValue = "0", sendEvents = false, name = "InstanceID")
    private var instanceId: UnsignedIntegerFourBytes? = null

    /**
     * 声道状态变量
     * 
     * 用于指定音频通道。可能的值包括"Master"、"Left"、"Right"等。
     * 默认值为"Master"，表示主通道。不发送事件通知。
     */
    @UpnpStateVariable(defaultValue = "Master", sendEvents = false, name = "Channel")
    private var channel: String? = null

    /**
     * 音量状态变量
     * 
     * 表示当前设备的音量级别。
     * 使用UnsignedIntegerTwoBytes类型，确保与UPnP规范兼容。
     * 默认值为0，表示静音状态。
     */
    @UpnpStateVariable(defaultValue = "0")
    private var volume: UnsignedIntegerTwoBytes? = null

    /**
     * 静音状态变量
     * 
     * 表示设备当前是否处于静音状态。
     * "0"表示非静音，"1"表示静音。
     * 默认值为"0"（非静音）。
     */
    @UpnpStateVariable(defaultValue = "0", datatype = "boolean")
    private var mute: Boolean = false

    /**
     * 设置音量
     * 
     * 允许控制点设置设备的音量级别。
     * 
     * @param instanceId 目标服务实例的ID
     * @param channel 要设置音量的音频通道
     * @param desiredVolume 期望设置的音量值
     */
    @UpnpAction
    fun setVolume(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String,
        @UpnpInputArgument(name = "DesiredVolume") desiredVolume: UnsignedIntegerTwoBytes
    ) {
        this.volume = desiredVolume
    }

    /**
     * 设置静音状态
     * 
     * 允许控制点设置设备的静音状态。
     * 
     * @param instanceId 目标服务实例的ID
     * @param channel 要设置静音状态的音频通道
     * @param desiredMute 期望的静音状态，"1"为静音，"0"为非静音
     */
    @UpnpAction
    fun setMute(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String,
        @UpnpInputArgument(name = "DesiredMute") desiredMute: Boolean
    ) {
        this.mute = desiredMute
    }

    /**
     * 获取当前音量
     * 
     * 允许控制点查询设备的当前音量级别。
     * 
     * @param instanceId 目标服务实例的ID
     * @param channel 要查询音量的音频通道
     * @return 当前的音量级别
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentVolume")])
    fun getVolume(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String
    ): UnsignedIntegerTwoBytes? {
        return volume
    }

    /**
     * 获取当前静音状态
     * 
     * 允许控制点查询设备的当前静音状态。
     * 
     * @param instanceId 目标服务实例的ID
     * @param channel 要查询静音状态的音频通道
     * @return 当前的静音状态，"1"为静音，"0"为非静音
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentMute")])
    fun getMute(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String
    ): Boolean {
        return mute
    }
} 