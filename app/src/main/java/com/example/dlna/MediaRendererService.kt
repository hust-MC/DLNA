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
    
    /** 日志标签 */
    private val TAG = "MediaRendererService"
    
    init {
        // 在初始化时保存实例引用
        serviceInstance = this
        Log.d(TAG, "MediaRendererService实例已创建")
    }

    companion object {
        private const val TAG = "MediaRendererService_Static"
        
        /** 应用上下文引用 */
        private var appContext: Context? = null
        
        /** 媒体播放器管理器 */
        private var mediaPlayerManager: MediaPlayerManager? = null
        
        /** 服务实例引用 */
        private var serviceInstance: MediaRendererService? = null
        
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
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
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
                    // 使用Handler确保在主线程上更新
                    Handler(Looper.getMainLooper()).post {
                        try {
                            val transportState = when (state) {
                                MediaPlayerManager.PlaybackState.PLAYING -> "PLAYING"
                                MediaPlayerManager.PlaybackState.PAUSED -> "PAUSED_PLAYBACK"
                                MediaPlayerManager.PlaybackState.STOPPED -> "STOPPED"
                                MediaPlayerManager.PlaybackState.ERROR -> "STOPPED"
                            }
                            serviceInstance?.currentTransportState = transportState
                            Log.d(TAG, "播放状态更新为: $transportState")
                        } catch (e: Exception) {
                            Log.e(TAG, "更新播放状态失败", e)
                        }
                    }
                }

                override fun onPlaybackCompleted() {
                    // 使用Handler确保在主线程上更新
                    Handler(Looper.getMainLooper()).post {
                        try {
                            serviceInstance?.currentTransportState = "STOPPED"
                            Log.d(TAG, "播放完成，状态更新为停止")
                        } catch (e: Exception) {
                            Log.e(TAG, "更新播放完成状态失败", e)
                        }
                    }
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
            })
        }
    }

    /**
     * 与输出参数对应的状态变量
     */
    @UpnpStateVariable(defaultValue = "0", sendEvents = false, name = "InstanceID")
    private var instanceId: UnsignedIntegerFourBytes? = null

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

    /** 设置AV传输URI */
    @UpnpAction
    fun setAVTransportURI(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "CurrentURI") currentURI: String,
        @UpnpInputArgument(name = "CurrentURIMetaData") currentURIMetaData: String
    ) {
        Log.d(TAG, "设置AV传输URI: $currentURI")
        Log.d(TAG, "元数据: $currentURIMetaData")
        
        this.currentURI = currentURI
        this.currentURIMetaData = currentURIMetaData
        
        // 清除之前的状态
        this.mediaDuration = "00:00:00"
        this.currentMediaDuration = "00:00:00"
        this.currentTrackDuration = "00:00:00"
        
        try {
            // 使用自动播放功能设置媒体URI
            MediaRendererService.mediaPlayerManager?.setMediaURIAndPlay(currentURI)
            Log.d(TAG, "已设置媒体URI并将自动播放")
        } catch (e: Exception) {
            Log.e(TAG, "设置媒体URI失败: ${e.message}", e)
        }
    }

    /** 播放 */
    @UpnpAction
    fun play(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Speed") speed: String
    ) {
        Log.d(TAG, "播放命令接收，速度: $speed")
        this.speed = speed
        MediaRendererService.mediaPlayerManager?.play()
    }

    /** 暂停 */
    @UpnpAction
    fun pause(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes
    ) {
        Log.d(TAG, "暂停命令接收")
        MediaRendererService.mediaPlayerManager?.pause()
    }

    /** 停止 */
    @UpnpAction
    fun stop(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes
    ) {
        Log.d(TAG, "停止命令接收")
        MediaRendererService.mediaPlayerManager?.stop()
    }

    /** 跳转到指定位置 */
    @UpnpAction
    fun seek(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Unit") unit: String,
        @UpnpInputArgument(name = "Target") target: String
    ) {
        Log.d(TAG, "跳转命令接收: $unit, $target")
        this.unit = unit
        this.target = target
        
        if (unit == "REL_TIME" || unit == "ABS_TIME") {
            val seconds = MediaRendererService.parseTimeString(target)
            MediaRendererService.mediaPlayerManager?.seekTo(seconds * 1000)
        }
    }

    /** 获取传输信息 */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentTransportState")])
    fun getTransportInfo(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes
    ): String {
        Log.d(TAG, "获取传输信息")
        return currentTransportState
    }

    /** 获取媒体信息 */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentURI")])
    fun getMediaInfo(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes
    ): String {
        Log.d(TAG, "获取媒体信息")
        return currentURI
    }

    /** 获取位置信息 */
    @UpnpAction(
        out = [
            UpnpOutputArgument(name = "AbsTime"),
            UpnpOutputArgument(name = "RelTime"),
            UpnpOutputArgument(name = "MediaDuration")
        ]
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