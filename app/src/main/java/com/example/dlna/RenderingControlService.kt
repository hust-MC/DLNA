package com.example.dlna

import android.content.Context
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
import com.max.videoplayer.MediaPlayerManager

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
class RenderingControlService(private val context: Context) {
    companion object {
        private const val TAG = "RenderingControlService"
        
        /** 最大音量值 */
        private const val MAX_VOLUME = 100
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

    /** 媒体播放管理器引用 */
    private var mediaPlayerManager: MediaPlayerManager? = null
    
    /**
     * 设置媒体播放器管理器
     * 
     * @param manager MediaPlayerManager实例
     */
    fun setMediaPlayerManager(manager: MediaPlayerManager) {
        Log.d(TAG, context.getString(R.string.log_set_media_player_manager))
        mediaPlayerManager = manager
    }

    /** 设置音量 */
    @UpnpAction
    fun setVolume(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String,
        @UpnpInputArgument(name = "DesiredVolume") desiredVolume: UnsignedIntegerTwoBytes
    ) {
        Log.d(TAG, context.getString(R.string.log_set_volume, desiredVolume))
        this.volume = desiredVolume
        
        // MediaPlayerManager中没有setVolume方法，所以我们这里只存储值，不执行操作
        // 将来如果需要控制音量，可以在MediaPlayerManager中添加相应方法
    }

    /** 设置静音状态 */
    @UpnpAction
    fun setMute(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String,
        @UpnpInputArgument(name = "DesiredMute") desiredMute: Boolean
    ) {
        Log.d(TAG, context.getString(R.string.log_set_mute, desiredMute.toString()))
        this.mute = desiredMute
        
        // MediaPlayerManager中没有setVolume方法，所以我们这里只存储值，不执行操作
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