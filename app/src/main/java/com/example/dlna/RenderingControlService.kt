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
import java.lang.ref.WeakReference

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

        /** UPnP音量最大值 */
        private const val MAX_VOLUME = 100

        /** 播放器管理器的弱引用，避免内存泄漏 */
        private var mediaPlayerManagerRef: WeakReference<MediaPlayerManager>? = null

        /**
         * 设置媒体播放器管理器
         *
         * @param manager MediaPlayerManager实例
         */
        fun setMediaPlayerManager(manager: MediaPlayerManager) {
            Log.d(TAG, "设置MediaPlayerManager")
            mediaPlayerManagerRef = WeakReference(manager)
        }
    }

    /** 音量状态变量 */
    @UpnpStateVariable(defaultValue = "50", datatype = "ui2")
    private var volume: UnsignedIntegerTwoBytes = UnsignedIntegerTwoBytes(50)

    /** 静音状态变量 */
    @UpnpStateVariable(defaultValue = "0", datatype = "boolean")
    private var mute: Boolean = false

    /** 实例ID状态变量 */
    @UpnpStateVariable(defaultValue = "0", sendEvents = false, name = "InstanceID")
    private var instanceId: UnsignedIntegerFourBytes? = null

    /** 声道状态变量 */
    @UpnpStateVariable(defaultValue = "Master", sendEvents = false, name = "Channel")
    private var channel: String? = null


    /** 设置音量 */
    @UpnpAction
    fun setVolume(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String,
        @UpnpInputArgument(name = "DesiredVolume") desiredVolume: UnsignedIntegerTwoBytes
    ) {
        Log.d(TAG, "设置音量: ${desiredVolume.value}")
        volume = desiredVolume

        // 转换音量：UPnP的0-100 → ExoPlayer的0.0-1.0
        val volumeFloat = desiredVolume.value.toFloat() / MAX_VOLUME

        // 静音状态下不改变实际音量
        if (!mute) {
            mediaPlayerManagerRef?.get()?.setVolume(volumeFloat)
        }
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

        // 静音时音量为0，取消静音时恢复当前音量
        val volumeFloat = if (desiredMute) 0f else (volume.value.toFloat() / MAX_VOLUME)
        mediaPlayerManagerRef?.get()?.setVolume(volumeFloat)
        Log.d(TAG, "静音状态: $desiredMute, 音量设为: $volumeFloat")
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