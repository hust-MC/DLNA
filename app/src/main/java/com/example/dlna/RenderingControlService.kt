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
    init {
        contextRef = WeakReference(context.applicationContext)
    }

    companion object {
        private const val TAG = "RenderingControlService"

        /** UPnP音量最大值 */
        private const val MAX_VOLUME = 100

        /** 播放器管理器的弱引用，避免内存泄漏 */
        private var mediaPlayerManagerRef: WeakReference<MediaPlayerManager>? = null

        /** 用于 Companion 内 getString 的 Context 弱引用 */
        private var contextRef: WeakReference<Context>? = null

        private fun getString(resId: Int, vararg formatArgs: Any): String {
            return contextRef?.get()?.getString(resId, *formatArgs) ?: ""
        }

        /**
         * 设置媒体播放器管理器，供后续音量/静音控制使用。
         *
         * @param manager MediaPlayerManager 实例，用于 setVolume
         * 无返回值。
         */
        fun setMediaPlayerManager(manager: MediaPlayerManager) {
            Log.d(TAG, getString(R.string.log_set_media_player_manager))
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


    /**
     * 设置音量（UPnP SetVolume）。将 UPnP 0～100 转为 ExoPlayer 0.0～1.0 并下发。
     *
     * @param instanceId    实例 ID（本实现未使用）
     * @param channel       声道（如 Master）
     * @param desiredVolume 目标音量 0～100
     * 无返回值。
     */
    @UpnpAction
    fun setVolume(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String,
        @UpnpInputArgument(name = "DesiredVolume") desiredVolume: UnsignedIntegerTwoBytes
    ) {
        Log.d(TAG, context.getString(R.string.log_set_volume, desiredVolume.value.toString()))
        volume = desiredVolume

        // 转换音量：UPnP的0-100 → ExoPlayer的0.0-1.0
        val volumeFloat = desiredVolume.value.toFloat() / MAX_VOLUME

        // 静音状态下不改变实际音量
        if (!mute) {
            mediaPlayerManagerRef?.get()?.setVolume(volumeFloat)
        }
    }

    /**
     * 设置静音状态（UPnP SetMute）。静音时下发 0，取消静音时恢复当前音量比例。
     *
     * @param instanceId  实例 ID（未使用）
     * @param channel     声道（未使用）
     * @param desiredMute true 静音，false 取消静音
     * 无返回值。
     */
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
        Log.d(TAG, context.getString(R.string.log_mute_state_volume, desiredMute.toString(), volumeFloat.toString()))
    }

    /**
     * 获取当前音量（UPnP GetVolume）。
     *
     * @param instanceId 实例 ID（未使用）
     * @param channel    声道（未使用）
     * @return 当前音量 0～100（UnsignedIntegerTwoBytes）
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentVolume")])
    fun getVolume(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String
    ): UnsignedIntegerTwoBytes {
        return volume
    }

    /**
     * 获取当前静音状态（UPnP GetMute）。
     *
     * @param instanceId 实例 ID（未使用）
     * @param channel    声道（未使用）
     * @return 当前是否静音
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentMute")])
    fun getMute(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Channel") channel: String
    ): Boolean {
        return mute
    }
} 