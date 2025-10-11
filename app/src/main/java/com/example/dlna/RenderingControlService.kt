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
 * 实现DLNA音量控制功能，支持音量调节和静音控制。
 * 已测试兼容：爱奇艺、B站、腾讯视频、优酷等主流App。
 * 
 * @param context Android上下文
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
         * 设置播放器管理器
         * 由VideoPlayerActivity在onCreate时调用
         */
        fun setMediaPlayerManager(manager: MediaPlayerManager) {
            mediaPlayerManagerRef = WeakReference(manager)
            Log.d(TAG, "MediaPlayerManager已设置")
        }
    }

    // ========== UPnP状态变量 ==========
    
    /**
     * 音量状态变量（0-100）
     * UPnP规范要求使用ui2类型
     */
    @UpnpStateVariable(
        name = "Volume",
        datatype = "ui2",
        defaultValue = "50",
        sendEvents = false
    )
    private var volume = UnsignedIntegerTwoBytes(50)

    /**
     * 静音状态变量
     * false=正常播放，true=静音
     */
    @UpnpStateVariable(
        name = "Mute",
        datatype = "boolean",
        defaultValue = "0",
        sendEvents = false
    )
    private var mute = false
    
    // UPnP规范要求的参数类型变量（供Cling框架内部使用）
    @UpnpStateVariable(sendEvents = false, name = "A_ARG_TYPE_InstanceID", datatype = "ui4")
    private var argTypeInstanceID = UnsignedIntegerFourBytes(0)
    
    @UpnpStateVariable(sendEvents = false, name = "A_ARG_TYPE_Channel")
    private var argTypeChannel = "Master"

    // ========== UPnP动作实现 ==========

    /**
     * 设置音量
     * 
     * @param instanceId 实例ID（通常为0）
     * @param channel 声道（通常为"Master"）
     * @param desiredVolume 目标音量（0-100）
     */
    @UpnpAction
    fun setVolume(
        @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") 
        instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel", stateVariable = "A_ARG_TYPE_Channel") 
        channel: String,
        @UpnpInputArgument(name = "DesiredVolume", stateVariable = "Volume") 
        desiredVolume: UnsignedIntegerTwoBytes
    ) {
        Log.d(TAG, "设置音量: ${desiredVolume.value}")
        volume = desiredVolume

        // 转换为ExoPlayer的音量范围（0.0-1.0）
        val volumeFloat = desiredVolume.value.toFloat() / MAX_VOLUME
        
        // 静音状态下不改变实际音量
        if (!mute) {
            mediaPlayerManagerRef?.get()?.setVolume(volumeFloat)
        }
    }

    /**
     * 获取当前音量
     * 
     * @return 当前音量值（0-100）
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentVolume", stateVariable = "Volume")])
    fun getVolume(
        @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") 
        instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel", stateVariable = "A_ARG_TYPE_Channel") 
        channel: String
    ): UnsignedIntegerTwoBytes {
        return volume
    }

    /**
     * 设置静音状态
     * 
     * @param desiredMute true=静音，false=取消静音
     */
    @UpnpAction
    fun setMute(
        @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") 
        instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel", stateVariable = "A_ARG_TYPE_Channel") 
        channel: String,
        @UpnpInputArgument(name = "DesiredMute", stateVariable = "Mute") 
        desiredMute: Boolean
    ) {
        Log.d(TAG, "设置静音: $desiredMute")
        mute = desiredMute
        
        // 静音时音量为0，取消静音时恢复当前音量
        val volumeFloat = if (desiredMute) 0f else (volume.value.toFloat() / MAX_VOLUME)
        mediaPlayerManagerRef?.get()?.setVolume(volumeFloat)
    }

    /**
     * 获取当前静音状态
     * 
     * @return 当前是否静音
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentMute", stateVariable = "Mute")])
    fun getMute(
        @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID") 
        instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel", stateVariable = "A_ARG_TYPE_Channel") 
        channel: String
    ): Boolean {
        return mute
    }
} 