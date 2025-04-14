package com.example.dlna

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.fourthline.cling.binding.annotations.UpnpAction
import org.fourthline.cling.binding.annotations.UpnpInputArgument
import org.fourthline.cling.binding.annotations.UpnpOutputArgument
import org.fourthline.cling.binding.annotations.UpnpService
import org.fourthline.cling.binding.annotations.UpnpServiceId
import org.fourthline.cling.binding.annotations.UpnpServiceType
import org.fourthline.cling.binding.annotations.UpnpStateVariable
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes
import java.util.Locale

/**
 * UPnP媒体渲染服务
 *
 * 该服务实现了UPnP AVTransport服务规范，用于控制媒体播放和传输。
 * 它处理媒体URI的设置、播放控制（播放、暂停、停止）以及进度控制（跳转）等功能。
 * @author Max
 */
@UpnpService(
    serviceId = UpnpServiceId("AVTransport"),
    serviceType = UpnpServiceType(value = "AVTransport", version = 1)
)
class MediaRendererService {

    init {
        // 在初始化时保存实例引用
        serviceInstance = this
        Log.d(TAG, "MediaRendererService实例已创建")
    }

    companion object {
        private const val TAG = "MediaRendererService"

        /** 应用上下文引用 */
        private var appContext: Context? = null

        /** 媒体播放器管理器 */
        private var mediaPlayerManager: MediaPlayerManager? = null

        /** 服务实例引用 */
        private var serviceInstance: MediaRendererService? = null

        /** 状态变更对象引用 */
        private var transportLastChange: TransportLastChange? = null

        /**
         * 初始化服务
         *
         * 设置应用上下文并初始化媒体播放器
         *
         * @param context 应用上下文
         */
        fun initialize(context: Context) {
            appContext = context.applicationContext
            Log.d(TAG, "MediaRendererService已初始化")
        }

        /**
         * 设置媒体播放器管理器
         *
         * @param manager MediaPlayerManager实例
         */
        fun setMediaPlayerManager(manager: MediaPlayerManager) {
            mediaPlayerManager = manager
            initializePlayerListeners()
            Log.d(TAG, "已设置MediaPlayerManager")
        }

        /**
         * 格式化时间为00:00:00格式
         *
         * @param durationMs 毫秒时长
         * @return 格式化后的时间字符串
         */
        fun formatTime(durationMs: Int): String {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }

        /**
         * 解析时间字符串
         *
         * @param timeStr 时间字符串(HH:MM:SS格式)
         * @return 时间总秒数
         */
        fun parseTimeString(timeStr: String): Int {
            try {
                val parts = timeStr.split(":")
                if (parts.size == 3) {
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    val seconds = parts[2].toInt()
                    return (hours * 3600 + minutes * 60 + seconds)
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析时间字符串失败: $timeStr", e)
            }
            return 0
        }

        /**
         * 初始化播放器监听器
         *
         * 设置媒体播放器状态变化监听器
         */
        private fun initializePlayerListeners() {
            mediaPlayerManager?.setStateListener(object : MediaPlayerManager.MediaStateListener {
                override fun onPrepared(durationMs: Int) {
                    val formattedDuration = formatTime(durationMs)
                    Log.d(TAG, "媒体准备完成，时长: $formattedDuration")

                    // 使用Handler确保在主线程上更新
                    Handler(Looper.getMainLooper()).post {
                        try {
                            serviceInstance?.apply {
                                currentMediaDuration = formattedDuration
                                mediaDuration = formattedDuration
                                currentTrackDuration = formattedDuration
                            }
                            Log.d(TAG, "更新媒体时长: $formattedDuration")
                        } catch (e: Exception) {
                            Log.e(TAG, "更新媒体时长失败", e)
                        }
                    }
                }

                override fun onProgressUpdate(positionMs: Int) {
                    // 这里可以处理进度更新
                }

                override fun onPlaybackStateChanged(state: MediaPlayerManager.PlaybackState) {
                    when (state) {
                        MediaPlayerManager.PlaybackState.PLAYING -> {
                            Log.d(TAG, "播放状态变化: 播放中")
                            transportLastChange?.setPlaying()
                        }

                        MediaPlayerManager.PlaybackState.PAUSED -> {
                            Log.d(TAG, "播放状态变化: 已暂停")
                            transportLastChange?.setPaused()
                        }

                        MediaPlayerManager.PlaybackState.STOPPED -> {
                            Log.d(TAG, "播放状态变化: 已停止")
                            transportLastChange?.setStopped()
                        }

                        MediaPlayerManager.PlaybackState.ERROR -> {
                            Log.d(TAG, "播放状态变化: 播放错误")
                            transportLastChange?.setError()
                        }
                    }
                }

                override fun onPlaybackCompleted() {
                    Log.d(TAG, "播放完成")
                    transportLastChange?.setStopped()
                }

                override fun onError(errorMsg: String) {
                    Log.e(TAG, "播放错误: $errorMsg")
                    // 使用Handler确保在主线程上更新
                    Handler(Looper.getMainLooper()).post {
                        try {
                            serviceInstance?.currentTransportState = "STOPPED"
                            Log.d(TAG, "播放错误，状态更新为停止")
                        } catch (e: Exception) {
                            Log.e(TAG, "更新错误状态失败", e)
                        }
                    }
                }

                override fun onBufferingUpdate(percent: Int) {
                    Log.e(TAG, "onBufferingUpdate: $percent")

                }
            })
        }
    }

    /**
     * 与输出参数对应的状态变量
     */
    @UpnpStateVariable(defaultValue = "0", sendEvents = false, name = "InstanceID")
    private var instanceId: UnsignedIntegerFourBytes? = null

    @UpnpStateVariable(defaultValue = "")
    private var transportLastChange = TransportLastChange()

    @UpnpStateVariable(defaultValue = "STOPPED")
    private var currentTransportState: String = "STOPPED"

    @UpnpStateVariable(defaultValue = "0")
    private var currentTrack: String = "0"

    @UpnpStateVariable(defaultValue = "00:00:00")
    private var currentTrackDuration: String = "00:00:00"

    @UpnpStateVariable(defaultValue = "00:00:00")
    private var mediaDuration: String = "00:00:00"

    @UpnpStateVariable(defaultValue = "00:00:00")
    private var currentMediaDuration: String = "00:00:00"

    @UpnpStateVariable(defaultValue = "")
    private var currentURI: String = ""

    @UpnpStateVariable(defaultValue = "")
    private var currentURIMetaData: String = ""

    @UpnpStateVariable(defaultValue = "1")
    private var speed: String = "1"

    @UpnpStateVariable(defaultValue = "REL_TIME")
    private var unit: String = "REL_TIME"

    @UpnpStateVariable(defaultValue = "00:00:00")
    private var target: String = "00:00:00"

    @UpnpStateVariable(defaultValue = "00:00:00")
    private var absTime: String = "00:00:00"

    @UpnpStateVariable(defaultValue = "00:00:00")
    private var relTime: String = "00:00:00"

    /**
     * 设置URI动作
     *
     * 设置要播放的媒体资源URI
     *
     * @param instanceId 实例ID
     * @param uri 媒体资源URI
     * @param metadata 媒体资源元数据
     */
    @UpnpAction
    fun setAVTransportURI(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "CurrentURI") uri: String,
        @UpnpInputArgument(name = "CurrentURIMetaData") metadata: String
    ) {
        Log.d(TAG, "接收到设置URI请求: $uri")

        this.instanceId = instanceId
        this.currentURI = uri
        this.currentURIMetaData = metadata

        // 重置播放状态
        this.currentTransportState = "STOPPED"
    }

    /**
     * 播放动作
     *
     * 开始播放当前设置的媒体
     *
     * @param instanceId 实例ID
     * @param speed 播放速度
     */
    @UpnpAction
    fun play(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Speed") speed: String
    ) {
        Log.d(TAG, "接收到播放请求，速度: $speed")
        this.speed = speed

        // 使用Handler确保在主线程上调用播放
        Handler(Looper.getMainLooper()).post {
            try {
                // 更新状态
                this@MediaRendererService.currentTransportState = "PLAYING"

                // 如果尚未打开播放页面，则启动视频播放页面
                if (currentURI.isNotEmpty()) {
                    appContext?.let { context ->
                        VideoPlayerActivity.start(context, currentURI)
                        Log.d(TAG, "播放时启动视频播放页面")
                    }
                }

                Log.d(TAG, "已开始播放")
            } catch (e: Exception) {
                Log.e(TAG, "播放失败: ${e.message}", e)
            }
        }
    }

    /**
     * 暂停动作
     *
     * 暂停当前播放的媒体
     *
     * @param instanceId 实例ID
     */
    @UpnpAction
    fun pause(@UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes) {
        Log.d(TAG, "接收到暂停请求")

        // 使用Handler确保在主线程上调用暂停
        Handler(Looper.getMainLooper()).post {
            try {
                mediaPlayerManager?.pause()

                // 更新状态
                this@MediaRendererService.currentTransportState = "PAUSED_PLAYBACK"

                Log.d(TAG, "已暂停播放")
            } catch (e: Exception) {
                Log.e(TAG, "暂停失败: ${e.message}", e)
            }
        }
    }

    /**
     * 停止动作
     *
     * 停止当前播放的媒体
     *
     * @param instanceId 实例ID
     */
    @UpnpAction
    fun stop(@UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes) {
        Log.d(TAG, "接收到停止请求")

        // 使用Handler确保在主线程上调用停止
        Handler(Looper.getMainLooper()).post {
            try {
                mediaPlayerManager?.stop()

                // 更新状态
                this@MediaRendererService.currentTransportState = "STOPPED"

                Log.d(TAG, "已停止播放")
            } catch (e: Exception) {
                Log.e(TAG, "停止失败: ${e.message}", e)
            }
        }
    }

    /**
     * 跳转动作
     *
     * 将播放位置跳转到指定时间
     *
     * @param instanceId 实例ID
     * @param unit 时间单位
     * @param target 目标位置
     */
    @UpnpAction
    fun seek(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Unit") unit: String,
        @UpnpInputArgument(name = "Target") target: String
    ) {
        Log.d(TAG, "接收到跳转请求: 单位=$unit, 目标=$target")

        this.unit = unit
        this.target = target

        // 使用Handler确保在主线程上调用跳转
        Handler(Looper.getMainLooper()).post {
            try {
                if (unit == "REL_TIME" || unit == "ABS_TIME") {
                    val seconds = MediaRendererService.parseTimeString(target)
                    mediaPlayerManager?.seekTo(seconds * 1000)

                    Log.d(TAG, "已跳转到 ${seconds}秒")
                }
            } catch (e: Exception) {
                Log.e(TAG, "跳转失败: ${e.message}", e)
            }
        }
    }

    /** 获取传输信息 */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentTransportState")])
    fun getTransportInfo(@UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes): String {
        return currentTransportState
    }

    /** 获取媒体信息 */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentURI")])
    fun getMediaInfo(@UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes): String {
        return currentURI
    }

    /** 获取位置信息 */
    @UpnpAction(
        out = [UpnpOutputArgument(name = "AbsTime"), UpnpOutputArgument(name = "RelTime"), UpnpOutputArgument(
            name = "MediaDuration"
        )]
    )
    fun getPositionInfo(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes
    ): Array<String> {
        val currentPosition = MediaRendererService.mediaPlayerManager?.getCurrentPosition() ?: 0
        val formattedPosition = MediaRendererService.formatTime(currentPosition)

        this.absTime = formattedPosition
        this.relTime = formattedPosition

        Log.d(TAG, "获取位置信息: $formattedPosition / $mediaDuration")
        return arrayOf(absTime, relTime, mediaDuration)
    }
}

/**
 * 状态变更辅助类
 */
class TransportLastChange {
    // 设置状态为播放中
    fun setPlaying() {
        Log.d("TransportLastChange", "设置状态: 播放中")
    }

    // 设置状态为暂停
    fun setPaused() {
        Log.d("TransportLastChange", "设置状态: 已暂停")
    }

    // 设置状态为停止
    fun setStopped() {
        Log.d("TransportLastChange", "设置状态: 已停止")
    }

    // 设置状态为错误
    fun setError() {
        Log.d("TransportLastChange", "设置状态: 错误")
    }
} 