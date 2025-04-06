package com.example.dlna

import android.util.Log
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
    companion object {
        private const val TAG = "RenderingControlService"
        
        /** 最大音量值 */
        private const val MAX_VOLUME = 100
        
        /** 媒体播放管理器引用 */
        private var mediaPlayerManager: MediaPlayerManager? = null
        
        /**
         * 设置媒体播放器管理器
         * 
         * @param manager MediaPlayerManager实例
         */
        fun setMediaPlayerManager(manager: MediaPlayerManager) {
            mediaPlayerManager = manager
            Log.d(TAG, "已设置媒体播放器管理器")
        }
    }

    /** 实例ID状态变量 */
    @UpnpStateVariable(defaultValue = "0", sendEvents = false, name = "InstanceID")
    private var instanceId: UnsignedIntegerFourBytes? = null

    /** 声道状态变量 */
    @UpnpStateVariable(defaultValue = "Master", sendEvents = false, name = "Channel")
    private var channel: String? = null

    /** 音量状态变量 */
    @UpnpStateVariable(defaultValue = "50", datatype = "ui2")
    private var volume: UnsignedIntegerTwoBytes = UnsignedIntegerTwoBytes(50)

    /** 静音状态变量 */
    @UpnpStateVariable(defaultValue = "0", datatype = "boolean")
    private var mute: Boolean = false

    /** 设置音量 */
    @UpnpAction
    fun setVolume(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String,
        @UpnpInputArgument(name = "DesiredVolume") desiredVolume: UnsignedIntegerTwoBytes
    ) {
        Log.d(TAG, "设置音量: $desiredVolume")
        this.volume = desiredVolume
        
        // 将UPnP音量值(0-100)转换为MediaPlayer音量值(0.0-1.0)
        val normalizedVolume = desiredVolume.value.toFloat() / MAX_VOLUME
        mediaPlayerManager?.setVolume(normalizedVolume)
    }

    /** 设置静音状态 */
    @UpnpAction
    fun setMute(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String,
        @UpnpInputArgument(name = "DesiredMute") desiredMute: Boolean
    ) {
        Log.d(TAG, "设置静音: $desiredMute")
        this.mute = desiredMute
        
        // 根据静音设置调整音量
        if (desiredMute == true) {
            mediaPlayerManager?.setVolume(0f) // 静音
        } else {
            // 恢复之前的音量
            val normalizedVolume = this.volume.value.toFloat() / MAX_VOLUME
            mediaPlayerManager?.setVolume(normalizedVolume)
        }
    }

    /** 获取当前音量 */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentVolume")])
    fun getVolume(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String
    ): UnsignedIntegerTwoBytes {
        return volume
    }

    /** 获取当前静音状态 */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentMute")])
    fun getMute(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String
    ): Boolean {
        return mute
    }
} 